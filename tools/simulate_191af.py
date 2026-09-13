#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
191AF+ 模拟器（PC 端）—— 无真机时验证 App 整链路用。

寄存器语义按 **实机实测版（2026-09，取代厂家文档）**，与
app/src/main/java/com/quick/app/net/Registers.kt 一一对应：

    0x00 实时温度 ×0.1 ℃        0x01 温度单位（0=℃ 1=℉）   0x02 漏地电压 ×0.1 mV
    0x03 自动关机（分钟）        0x04 判定（0=NG，非 0=OK）    0x05~0x09 备用
    0x0A~0x19 设备信息字符串（32 字节 ASCII）
    0x1A 未知（待实测，保持 0）   0x1B 目标温度 ℃
    0x1C 温度下限 ℃             0x1D 温度上限 ℃
    0x1E 结果保存标志：未按=0，按下=0x14(20)，保持 HOLD_SEC 秒后自清

MODBUS TCP/IP 服务端：FC03 读保持寄存器（支持一次全读 31 个 0x00~0x1E）、FC06 写单个。

控制台命令：
    s [温度] [ok|ng]   模拟操作员按仪器保存键：写 0x1E=0x14 + 0x04 判定，HOLD_SEC 秒后 0x1E 自清
    t <温度℃>          改实时温度 0x00
    l <mV>             改漏地电压 0x02
    set <寄存器hex> <值>  直接写寄存器（调试）
    show               显示寄存器内存
    h                  帮助    q  退出

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

TOTAL = 0x1F  # 31 个寄存器（0x00~0x1E），App 一次全读

TEMP = 0x00
UNIT = 0x01
LEAKAGE = 0x02
AUTO_OFF = 0x03
JUDGE = 0x04
INFO_START = 0x0A
INFO_COUNT = 16
TARGET_TEMP = 0x1B
TEMP_LOW = 0x1C
TEMP_HIGH = 0x1D
SAVE_FLAG = 0x1E
SAVE_FLAG_VALUE = 0x14       # 实测：按下保存键时 0x1E = 0x0014

HOLD_SEC = 3.0               # 0x1E 保持时长真机未实测，模拟器取 3 秒（0.5s 轮询足以抓到）


class Device:
    def __init__(self):
        self.regs = [0] * TOTAL
        self.regs[UNIT] = 0            # ℃
        self.regs[TEMP] = 218          # 21.8 ℃ 室温
        self.regs[LEAKAGE] = 12        # 1.2 mV
        self.regs[AUTO_OFF] = 0        # 不自动关机
        self.regs[JUDGE] = 0           # 尚无判定
        self.regs[TARGET_TEMP] = 350   # 目标 350 ℃
        self.regs[TEMP_LOW] = 340      # 合格区间 340~360 ℃
        self.regs[TEMP_HIGH] = 360
        info = b"QK-191AF+ SN0001"
        buf = info + b"\x00" * (INFO_COUNT * 2 - len(info))
        for i in range(INFO_COUNT):
            self.regs[INFO_START + i] = int.from_bytes(buf[i * 2:i * 2 + 2], "big")
        self.lock = threading.Lock()
        self.hold_timer = None
        self.ok_flip = False           # 真机 OK 值见过 1 与 2 → 交替给，逼 App 按「非 0」判定

    # ---- 命令（操作员动作） ----
    def press_save(self, temp=None, judge=None):
        """模拟操作员在仪器上按「保存」：0x1E 置 0x14，held HOLD_SEC 秒后自清"""
        with self.lock:
            if temp is not None:
                # 实测：0x00 是不停刷新的实时温度；保存瞬间定格的那个温度寄存器尚未测出，
                # App 侧暂用实时温度代替，故模拟器把 0x00 也设成刚测的值，行为等价。
                self.regs[TEMP] = int(round(temp * 10))
            if judge is None:
                ok = self.regs[TEMP_LOW] * 10 <= self.regs[TEMP] <= self.regs[TEMP_HIGH] * 10
            elif isinstance(judge, str):
                ok = judge.lower() in ("ok", "1", "true")
            else:
                ok = bool(judge)
            if ok:
                self.ok_flip = not self.ok_flip
                self.regs[JUDGE] = 2 if self.ok_flip else 1     # 非 0 即 OK
            else:
                self.regs[JUDGE] = 0                            # 0 = NG
            self.regs[SAVE_FLAG] = SAVE_FLAG_VALUE
            self._arm_clear()
            print(f"[模拟保存] 0x1E=0x{SAVE_FLAG_VALUE:02X} 0x04={self.regs[JUDGE]} "
                  f"({('OK' if self.regs[JUDGE] else 'NG')}) 0x00={self.regs[TEMP] / 10:.1f}℃")
            print(f"           → {HOLD_SEC:.0f}s 后 0x1E 自动清零（模拟仪器保持期）")

    def _arm_clear(self):
        if self.hold_timer is not None:
            self.hold_timer.cancel()
        self.hold_timer = threading.Timer(HOLD_SEC, self._clear_save_flag)
        self.hold_timer.daemon = True
        self.hold_timer.start()

    def _clear_save_flag(self):
        with self.lock:
            self.regs[SAVE_FLAG] = 0
        print("[保持结束] 0x1E 已自清为 0")

    def set_live_temp(self, celsius):
        with self.lock:
            self.regs[TEMP] = int(round(celsius * 10))
        print(f"[测温] 0x00 = {self.regs[TEMP]} ({self.regs[TEMP] / 10:.1f}℃)")

    def set_leakage(self, mv):
        with self.lock:
            self.regs[LEAKAGE] = int(round(mv * 10))
        print(f"[漏地电压] 0x02 = {self.regs[LEAKAGE]} ({self.regs[LEAKAGE] / 10:.1f} mV)")

    def dump(self):
        with self.lock:
            print("0x00 实时温度:", self.regs[TEMP] / 10, "℃ | 0x01 单位:",
                  "℃" if self.regs[UNIT] == 0 else "℉",
                  "| 0x02 漏地电压:", self.regs[LEAKAGE] / 10, "mV")
            print("0x03 自动关机:", self.regs[AUTO_OFF], "分钟 | 0x04 判定:",
                  "OK" if self.regs[JUDGE] else "NG", f"({self.regs[JUDGE]})")
            info = b"".join(self.regs[INFO_START + i].to_bytes(2, "big") for i in range(INFO_COUNT))
            print("0x0A~0x19 设备信息:", info.split(b"\x00")[0].decode("ascii", "replace"))
            print("0x1A 未知:", self.regs[0x1A],
                  "| 0x1B 目标:", self.regs[TARGET_TEMP], "℃",
                  "| 0x1C 下限:", self.regs[TEMP_LOW], "℃",
                  "| 0x1D 上限:", self.regs[TEMP_HIGH], "℃")
            print("0x1E 保存标志:", self.regs[SAVE_FLAG], "(非 0 = 刚按过保存)")

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
                temp = float(parts[1]) if len(parts) >= 2 else None
                judge = parts[2] if len(parts) > 2 else None
                dev.press_save(temp, judge)
            elif cmd == "t" and len(parts) >= 2:
                dev.set_live_temp(float(parts[1]))
            elif cmd == "l" and len(parts) >= 2:
                dev.set_leakage(float(parts[1]))
            elif cmd == "set" and len(parts) >= 3:
                with dev.lock:
                    dev.regs[int(parts[1], 16)] = int(parts[2])
                print("已写入")
            elif cmd == "show":
                dev.dump()
            elif cmd in ("h", "help"):
                print("命令: s [温度℃] [ok|ng] | t <温度℃> | l <mV> | "
                      "set <寄存器hex> <值> | show | q")
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
    print("控制台可输入命令（帮助: help）: s 351 ok → 模拟按保存键（351℃/OK）")
    dev.dump()
    cmd_loop(dev, host, port)
    stop.set()
    th.join(timeout=1)


if __name__ == "__main__":
    main()
