#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
191AF+/192AF 模拟器（PC 端）—— 无真机时验证 App 整链路用。

寄存器语义严格按厂方手册《地址分配》表（piture/file.webp 第 2 页），与
app/src/main/java/com/quick/app/net/Registers.kt 一一对应：

    0x00 实时温度 ×0.1 ℃        0x01 温度单位(0=℃ 1=℉)
    0x02 实时电压 ×0.1 mV       0x03 实时电阻 ×0.1 Ω
    0x04 当前测量通道 0=温度 1=漏电压 2=地对地电阻
    0x05~0x09 备用
    0x0A~0x19 设备信息(扫码) 32 字节 ASCII
    0x1A 设定温度 ℃             0x1B 温度判断下限 ℃      0x1C 温度判断上限 ℃
    0x1D 电压判断上限 ×0.1 mV   0x1E 电阻判断上限 ×0.1 Ω
    0x1F 测试上传标志：0=无效 1=保存按下（★触发；保持约 1 s 后自动清零）
    0x20 测试保存温度 ℃（标志=1 时有效）
    0x21 测试保存电压 ×0.1 mV   0x22 测试保存电阻 ×0.1 Ω
    0x23 测试合格判定 0=NG 1=OK 2=无效

MODBUS TCP/IP 服务端：FC03 读保持寄存器（一次全读 36 个 0x00~0x23）、FC06 写单个。

控制台命令（模拟操作员在仪器上操作）：
    s [温度℃] [ok|ng|inv] [miss]  模拟按「保存」：写 0x20~0x23 并置 0x1F=1，约 1 s 后自清；
                           加 miss 则**不置标志**（模拟 App 读晚了、标志窗口已过 → 验证补收通道）；
                           不传判定时按当前通道与判断上限自动判定
    t <温度℃>    改实时温度 0x00        l <mV>     改实时电压 0x02
    r <Ω>        改实时电阻 0x03        ch <0|1|2> 改测量通道 0x04
    set <寄存器hex> <值>   直接写寄存器（调试）      show  显示寄存器内存      q  退出

用法：
    python tools/simulate_191af.py                      # 监听 0.0.0.0:502
    python tools/simulate_191af.py 192.168.1.5 1502     # 指定地址与端口
"""
import socket
import sys
import threading
import time

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 502

# 0x1F 保持时长（秒）：实机实测约 1 s（2026-09-14 用户提供）。
# App 侧轮询周期须小于它（默认 0.9 s）；周期 + Wi-Fi 抖动若超出窗口，靠结果块签名补收。
HOLD_SECONDS = 1.0

TOTAL = 0x24  # 0x00~0x23 共 36 个，App 一次全读

LIVE_TEMP = 0x00
UNIT = 0x01
LIVE_VOLTAGE = 0x02
LIVE_RESISTANCE = 0x03
CHANNEL = 0x04
INFO_START = 0x0A
INFO_COUNT = 16
TARGET_TEMP = 0x1A
TEMP_LOW = 0x1B
TEMP_HIGH = 0x1C
VOLT_LIMIT = 0x1D
RES_LIMIT = 0x1E
UPLOAD_FLAG = 0x1F
SAVED_TEMP = 0x20
SAVED_VOLTAGE = 0x21
SAVED_RESISTANCE = 0x22
JUDGE = 0x23

CH_NAMES = {0: "温度", 1: "漏电压", 2: "地对地电阻"}


class Device:
    def __init__(self):
        self.regs = [0] * TOTAL
        self.regs[UNIT] = 0
        self.regs[LIVE_TEMP] = 218       # 21.8 ℃
        self.regs[LIVE_VOLTAGE] = 1      # 0.1 mV
        self.regs[LIVE_RESISTANCE] = 3   # 0.3 Ω
        self.regs[CHANNEL] = 0           # 温度通道
        self.regs[TARGET_TEMP] = 350
        self.regs[TEMP_LOW] = 340
        self.regs[TEMP_HIGH] = 360
        self.regs[VOLT_LIMIT] = 20       # 2.0 mV
        self.regs[RES_LIMIT] = 20        # 2.0 Ω
        info = b"QK-HT-001"
        buf = info + b"\x00" * (INFO_COUNT * 2 - len(info))
        for i in range(INFO_COUNT):
            self.regs[INFO_START + i] = int.from_bytes(buf[i * 2:i * 2 + 2], "big")
        self.lock = threading.Lock()
        self.flag_clear_at = 0.0   # 0x1F 的自动清零时刻（time.monotonic）

    # ---- 命令（操作员动作） ----
    def press_save(self, temp=None, judge=None, mark=True):
        """模拟操作员在仪器上按「保存」：填 0x20~0x23；mark=False 表示不置标志（App 读晚了）"""
        with self.lock:
            ch = self.regs[CHANNEL]
            if temp is not None:
                # 温度通道按给定值；其它通道仍按实时值定格
                self.regs[LIVE_TEMP] = int(round(temp * 10))
            self.regs[SAVED_TEMP] = int(round(self.regs[LIVE_TEMP] / 10))
            self.regs[SAVED_VOLTAGE] = self.regs[LIVE_VOLTAGE]
            self.regs[SAVED_RESISTANCE] = self.regs[LIVE_RESISTANCE]
            if judge is None:
                if ch == 1:
                    v = 1 if self.regs[LIVE_VOLTAGE] <= self.regs[VOLT_LIMIT] else 0
                elif ch == 2:
                    v = 1 if self.regs[LIVE_RESISTANCE] <= self.regs[RES_LIMIT] else 0
                else:
                    t = self.regs[SAVED_TEMP]
                    v = 1 if self.regs[TEMP_LOW] <= t <= self.regs[TEMP_HIGH] else 0
            elif isinstance(judge, str):
                v = {"ok": 1, "ng": 0, "inv": 2}.get(judge.lower(), 1)
            else:
                v = int(judge)
            self.regs[JUDGE] = v
            if mark:
                self.regs[UPLOAD_FLAG] = 1
                self.flag_clear_at = time.monotonic() + HOLD_SECONDS
        print(f"[模拟保存] 通道={CH_NAMES.get(ch, ch)} 0x1F={'1（保持 %.1fs 后自清）' % HOLD_SECONDS if mark else '0（未置标志：模拟读晚了）'} "
              f"0x20={self.regs[SAVED_TEMP]}℃ 0x21={self.regs[SAVED_VOLTAGE] / 10:.1f}mV "
              f"0x22={self.regs[SAVED_RESISTANCE] / 10:.1f}Ω 0x23={v}"
              f"({['NG', 'OK', '无效'][v] if v in (0, 1, 2) else v})")

    def set_val(self, addr, val, label, scale):
        with self.lock:
            self.regs[addr] = int(round(val * scale))
        print(f"[{label}] 0x{addr:02X} = {self.regs[addr]} ({self.regs[addr] / scale:.1f})")

    def dump(self):
        with self.lock:
            info = b"".join(self.regs[INFO_START + i].to_bytes(2, "big") for i in range(INFO_COUNT))
            print(f"0x00 实时温度 {self.regs[LIVE_TEMP] / 10:.1f}℃ | 0x01 单位 "
                  f"{'℃' if self.regs[UNIT] == 0 else '℉'} | 0x02 实时电压 {self.regs[LIVE_VOLTAGE] / 10:.1f}mV "
                  f"| 0x03 实时电阻 {self.regs[LIVE_RESISTANCE] / 10:.1f}Ω")
            print(f"0x04 测量通道 {self.regs[CHANNEL]}（{CH_NAMES.get(self.regs[CHANNEL], '?')}）")
            print("0x0A~0x19 设备信息:", info.split(b"\x00")[0].decode("ascii", "replace"))
            print(f"0x1A 设定温度 {self.regs[TARGET_TEMP]}℃ | 0x1B~0x1C 温度判断 "
                  f"{self.regs[TEMP_LOW]}~{self.regs[TEMP_HIGH]}℃")
            print(f"0x1D 电压上限 {self.regs[VOLT_LIMIT] / 10:.1f}mV | 0x1E 电阻上限 "
                  f"{self.regs[RES_LIMIT] / 10:.1f}Ω")
            left = self.flag_clear_at - time.monotonic()
            flag = f"{self.regs[UPLOAD_FLAG]}"
            if self.regs[UPLOAD_FLAG] != 0:
                flag += f"（还有 {left:.1f}s 自清）" if left > 0 else "（待自清）"
            print(f"0x1F 上传标志 {flag} | 0x20 保存温度 {self.regs[SAVED_TEMP]}℃ "
                  f"| 0x21 保存电压 {self.regs[SAVED_VOLTAGE] / 10:.1f}mV "
                  f"| 0x22 保存电阻 {self.regs[SAVED_RESISTANCE] / 10:.1f}Ω "
                  f"| 0x23 判定 {self.regs[JUDGE]}")

    # ---- MODBUS 处理 ----
    def handle_request(self, data):
        if len(data) < 12:
            return None
        tx = data[0:2]
        # 协议 id data[2:4] 应为 0
        unit = data[6]
        fc = data[7]
        if fc == 0x03:  # 读保持寄存器
            start = int.from_bytes(data[8:10], "big")
            qty = int.from_bytes(data[10:12], "big")
            if start + qty > TOTAL or qty > 125:
                return tx + b"\x00\x00\x00\x03" + bytes([unit, 0x83, 0x02])
            with self.lock:
                # 0x1F 由仪器自己的计时器清零（实测保持约 1 s），与是否被读取无关
                if self.regs[UPLOAD_FLAG] != 0 and time.monotonic() >= self.flag_clear_at:
                    self.regs[UPLOAD_FLAG] = 0
                    print("[自清] 0x1F 保持期结束，标志已清零")
                body = b"".join(v.to_bytes(2, "big") for v in self.regs[start:start + qty])
            pdu = bytes([0x03, len(body)]) + body
            return tx + b"\x00\x00" + (len(pdu) + 1).to_bytes(2, "big") + bytes([unit]) + pdu
        elif fc == 0x06:
            addr = int.from_bytes(data[8:10], "big")
            val = int.from_bytes(data[10:12], "big")
            if addr < TOTAL:
                with self.lock:
                    self.regs[addr] = val
                print(f"[写寄存器] 0x{addr:02X} = {val}")
                return tx + b"\x00\x00\x00\x06" + bytes([unit]) + data[7:12]
        return None  # 不支持的功能码：不回（模拟异常/超时场景由 App 观察）

    def serve(self, host, port, stop_event):
        srv = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        srv.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        srv.bind((host, port))
        srv.listen(1)
        srv.settimeout(0.5)
        print(f"[监听] {host}:{port}  (MODBUS TCP, 最多 1 客户端)")
        while not stop_event.is_set():
            try:
                conn, addr = srv.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            print(f"[连接] 客户端 {addr[0]}:{addr[1]}")
            conn.settimeout(0.5)
            try:
                while not stop_event.is_set():
                    try:
                        data = conn.recv(4096)
                    except socket.timeout:
                        continue
                    if not data:
                        break
                    resp = self.handle_request(data)
                    if resp:
                        conn.sendall(resp)
            except OSError:
                pass
            finally:
                try:
                    conn.close()
                except OSError:
                    pass
                print("[断开] 客户端")
        srv.close()


def cmd_loop(dev, host, port):
    while True:
        try:
            line = input("模拟器> ").strip()
        except (EOFError, KeyboardInterrupt):
            print("\n再见")
            return
        if not line:
            continue
        parts = line.split()
        cmd = parts[0].lower()
        try:
            if cmd == "s":
                mark = "miss" not in [p.lower() for p in parts[1:]]
                args = [p for p in parts[1:] if p.lower() != "miss"]
                temp = float(args[0]) if len(args) >= 1 else None
                judge = args[1] if len(args) >= 2 else None
                dev.press_save(temp, judge, mark)
            elif cmd == "t" and len(parts) >= 2:
                dev.set_val(LIVE_TEMP, float(parts[1]), "实时温度", 10)
            elif cmd == "l" and len(parts) >= 2:
                dev.set_val(LIVE_VOLTAGE, float(parts[1]), "实时电压", 10)
            elif cmd == "r" and len(parts) >= 2:
                dev.set_val(LIVE_RESISTANCE, float(parts[1]), "实时电阻", 10)
            elif cmd == "ch" and len(parts) >= 2:
                with dev.lock:
                    dev.regs[CHANNEL] = int(parts[1])
                print(f"[测量通道] 0x04 = {dev.regs[CHANNEL]}（{CH_NAMES.get(dev.regs[CHANNEL], '?')}）")
            elif cmd == "set" and len(parts) >= 3:
                with dev.lock:
                    dev.regs[int(parts[1], 16)] = int(parts[2])
                print("已写入")
            elif cmd == "show":
                dev.dump()
            elif cmd in ("h", "help"):
                print("命令: s [温度℃] [ok|ng|inv] [miss] | t <温度℃> | l <mV> | r <Ω> | "
                      "ch <0|1|2> | set <寄存器hex> <值> | show | q")
            elif cmd == "q":
                return
            else:
                print("未知命令（help 查看）")
        except (ValueError, IndexError) as e:
            print("参数错误:", e)


def main():
    host = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_HOST
    port = int(sys.argv[2]) if len(sys.argv) > 2 else DEFAULT_PORT
    dev = Device()
    stop = threading.Event()
    th = threading.Thread(target=dev.serve, args=(host, port, stop), daemon=True)
    th.start()
    time.sleep(0.3)
    print("控制台可输入命令（帮助: help）: s 351 ok → 模拟按保存（351℃/OK）；"
          "s 351 ok miss → 模拟标志窗口已过（验证补收）")
    dev.dump()
    cmd_loop(dev, host, port)
    stop.set()
    th.join(timeout=1)


if __name__ == "__main__":
    main()
