#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
191AF+ 模拟器（PC 端）—— 无真机时验证 App 整链路用。

行为按《191AF 系列通讯协议 V1.1》实现：
- MODBUS TCP/IP 服务端，监听 502（默认）
- FC03 读保持寄存器（支持一次全读 31 个 0x00~0x1E）
- 0x1C 测试上传标志：模拟器按文档语义"读取后自动清零"
  （读取范围覆盖 0x1C 的请求处理后自动清零 —— 现场实测前 App 的假设）

控制台命令（模拟操作员在仪器上按保存）：
    s <温度> [ok|ng]   模拟"保存按钮按下"：设 0x1C=1, 0x1D=<温度>, 0x1E=判定
                       （不传 ok/ng 时判定 = 温度在设定±误差内 => OK）
    set <寄存器号hex> <值>   直接写寄存器（调试）
    show                显示寄存器内存
    ip <地址>            换绑地址（重启监听，测试断线）
    q                   退出

用法：
    python tools/simulate_191af.py            # 监听 0.0.0.0:502
    python tools/simulate_191af.py 192.168.x.x 1502   # 指定地址与端口
"""
import socket
import sys
import threading
import time
import select

DEFAULT_HOST = "0.0.0.0"
DEFAULT_PORT = 502

TOTAL = 0x1F  # 31 个寄存器
RESULT_FLAG = 0x1C
SAVED_TEMP = 0x1D
JUDGE = 0x1E
SET_TEMP = 0x1A
TOLERANCE = 0x1B


class Device:
    def __init__(self):
        # 默认初始值（模拟已扫码配置过的仪器）
        self.regs = [0] * TOTAL
        self.regs[0x01] = 0          # 温度单位 ℃
        self.regs[0x02] = 0          # 手动保存
        self.regs[0x03] = 0          # 不自动关机
        self.regs[SET_TEMP] = 350    # 设定温度 350℃
        self.regs[TOLERANCE] = 10    # 误差 ±10℃
        sn = b"QK-HT-001"
        buf = sn + b"\x00" * (32 - len(sn))
        words = [int.from_bytes(buf[i:i + 2], "big") for i in range(0, 32, 2)]
        for i, w in enumerate(words):
            self.regs[0x0A + i] = w
        self.lock = threading.Lock()
        self.last_ok = None  # 最近判定（服务端日志用）
        self.saved_records = []  # 模拟"仪器只保留最新一笔"前可观察的历史（本工具不丢）

    # ---- 命令（操作员动作） ----
    def press_save(self, temp=None, judge=None):
        """模拟操作员按仪器保存按钮"""
        with self.lock:
            self.regs[RESULT_FLAG] = 1
            self.regs[SAVED_TEMP] = temp if temp is not None else self.regs[SET_TEMP]
            if judge is None:
                ok = abs(self.regs[SAVED_TEMP] - self.regs[SET_TEMP]) <= self.regs[TOLERANCE]
                self.regs[JUDGE] = 1 if ok else 0
            else:
                self.regs[JUDGE] = 1 if judge in ("ok", "OK", 1) else 0
            self.last_ok = self.regs[JUDGE]
        print(f"[模拟保存] 0x1C=1 0x1D={self.regs[SAVED_TEMP]} 0x1E={self.regs[JUDGE]}")

    def dump(self):
        with self.lock:
            print("0x00 实时温度:", self.regs[0x00] / 10, "℃")
            print("0x01 单位:", self.regs[0x01], "| 0x02 保存模式:", self.regs[0x02],
                  "| 0x03 自动关机:", self.regs[0x03], "分钟")
            print("0x1A 设定温度:", self.regs[SET_TEMP], "| 0x1B 误差:", self.regs[TOLERANCE])
            print("0x1C 上传标志:", self.regs[RESULT_FLAG], "| 0x1D 保存温度:", self.regs[SAVED_TEMP],
                  "| 0x1E 判定:", self.regs[JUDGE])

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
                # 文档语义：读取后自动清零 0x1C（覆盖 0x1C 的读取）
                if start <= RESULT_FLAG < start + qty and self.regs[RESULT_FLAG] == 1:
                    self.regs[RESULT_FLAG] = 0
                    print(f"[清零] 读取覆盖 0x1C，自动清零（快照已含同帧 0x1D/0x1E）")
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
                    print(f"[收] {data.hex(' ')}")
                    resp = self.handle_request(data)
                    if resp:
                        conn.sendall(resp)
                        print(f"[发] {resp.hex(' ')}")
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
            if cmd == "s" and len(parts) >= 2:
                dev.press_save(int(parts[1]), parts[2] if len(parts) > 2 else None)
            elif cmd == "t":  # 模拟测温枪读数变化（0x00 实时温度）
                dev.regs[0x00] = int(float(parts[1]) * 10)
                print(f"[测温] 0x00 = {dev.regs[0x00]} ({(dev.regs[0x00] / 10):.1f}℃)")
            elif cmd == "set" and len(parts) >= 3:
                dev.regs[int(parts[1], 16)] = int(parts[2])
                print("已写入")
            elif cmd == "show":
                dev.dump()
            elif cmd in ("h", "help"):
                print("命令: s <温度℃> [ok|ng] | t <温度℃> | set <寄存器hex> <值> | show | q")
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
    print("控制台可输入命令（帮助: help）: s 351 ok → 模拟按保存按钮（351℃/OK）")
    cmd_loop(dev, host, port)
    stop.set()
    th.join(timeout=1)


if __name__ == "__main__":
    main()
