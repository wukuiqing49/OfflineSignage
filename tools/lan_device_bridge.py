#!/usr/bin/env python3
"""
LAN Device Bridge for Android Emulator & Signage Device Discovery.
1. Proxies 0.0.0.0:8088 -> 127.0.0.1:8088 (Android Emulator)
2. Broadcasts and responds to UDP discovery (Port 18080) so that physical phones find the emulator automatically!
"""

import sys
import socket
import select
import threading
import json
import time

UDP_PORT = 18080
EMULATOR_FORWARD_PORT = 8088
PROXY_PORT = 8088

EMULATOR_DEVICE_ID = "8cf2ffe6-6704-4850-b744-5fb0b6efc6ff"
EMULATOR_DEVICE_NAME = "2号大屏 (Android虚拟机)"

def tcp_proxy():
    # Listen on 0.0.0.0:8089 or transparent proxy to 127.0.0.1:8088
    # Since adb forward owns 127.0.0.1:8088, let's bind on 0.0.0.0:8081 for LAN access and proxy to 127.0.0.1:8088
    lan_server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    lan_server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        lan_server.bind(("0.0.0.0", 8081))
        lan_server.listen(50)
        print("[*] TCP 局域网转发已就绪: 0.0.0.0:8081 -> 127.0.0.1:8088 (虚拟机)")
    except Exception as e:
        print(f"[!] TCP 绑定失败: {e}")
        return

    while True:
        try:
            client_sock, client_addr = lan_server.accept()
            threading.Thread(target=handle_proxy_client, args=(client_sock,), daemon=True).start()
        except Exception:
            break

def handle_proxy_client(client_sock):
    try:
        target_sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        target_sock.connect(("127.0.0.1", EMULATOR_FORWARD_PORT))
        sockets = [client_sock, target_sock]
        while True:
            r, _, _ = select.select(sockets, [], [], 10.0)
            if not r:
                break
            for s in r:
                other = target_sock if s is client_sock else client_sock
                data = s.recv(16384)
                if not data:
                    return
                other.sendall(data)
    except Exception:
        pass
    finally:
        client_sock.close()

def udp_discovery_loop():
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(("", UDP_PORT))
        print(f"[*] UDP 发现广播与响应服务已就绪 (端口 {UDP_PORT})")
    except Exception as e:
        print(f"[!] UDP 绑定失败: {e}")

    # Start broadcaster thread
    def broadcast_announcement():
        announcement = json.dumps({
            "version": 1,
            "type": "SIGNAGE",
            "deviceId": EMULATOR_DEVICE_ID,
            "deviceName": EMULATOR_DEVICE_NAME,
            "port": 8081
        }).encode("utf-8")

        while True:
            try:
                for b_addr in ["255.255.255.255", "192.168.31.255"]:
                    sock.sendto(announcement, (b_addr, UDP_PORT))
            except Exception:
                pass
            time.sleep(3)

    threading.Thread(target=broadcast_announcement, daemon=True).start()

    # Receiver loop for WHO_IS_SIGNAGE probes
    while True:
        try:
            data, addr = sock.recvfrom(2048)
            msg = json.loads(data.decode("utf-8"))
            if msg.get("type") == "WHO_IS_SIGNAGE":
                response = json.dumps({
                    "version": 1,
                    "type": "SIGNAGE",
                    "deviceId": EMULATOR_DEVICE_ID,
                    "deviceName": EMULATOR_DEVICE_NAME,
                    "port": 8081
                }).encode("utf-8")
                sock.sendto(response, addr)
                print(f"[*] 响应来自 {addr} 的设备发现扫描请求")
        except Exception:
            pass

def main():
    threading.Thread(target=tcp_proxy, daemon=True).start()
    udp_discovery_loop()

if __name__ == "__main__":
    main()
