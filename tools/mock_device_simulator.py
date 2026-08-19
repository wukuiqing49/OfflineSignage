#!/usr/bin/env python3
"""
Local Signage Virtual Device Simulator
Simulates one or more Signage devices on local ports (e.g. 8081, 8082).
Allows testing multi-device control with only 1 (or 0) physical Android devices.
"""

import sys
import os
import time
import json
import socket
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import urlparse, parse_qs, unquote

UDP_DISCOVERY_PORT = 18080
PROTOCOL_VERSION = 1

class VirtualSignageDevice:
    def __init__(self, device_id, device_name, port, pairing_token="123456"):
        self.device_id = device_id
        self.device_name = device_name
        self.port = port
        self.pairing_token = pairing_token
        self.current_resource = {
            "id": f"default_{device_id}",
            "name": f"{device_name} 默认画面",
            "kind": "TEXT",
            "content": f"欢迎光临\n{device_name} (Port {port})",
            "textColor": "#ffffff",
            "textBackgroundColor": "#1a3b30",
            "textSizeSp": 54,
            "fontFamily": "SYSTEM_SANS",
            "sourceUri": None
        }
        self.resources = {self.current_resource["id"]: self.current_resource}
        self.playing = True
        self.volume = 80
        self.muted = False
        self.command_revision = 1
        self.last_error = None
        self.http_server = None
        self.http_thread = None

    def start_http(self):
        device = self
        class Handler(BaseHTTPRequestHandler):
            def log_message(self, format, *args):
                pass # Quiet logs

            def send_json(self, data, status=200):
                body = json.dumps(data).encode("utf-8")
                self.send_response(status)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(body)))
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Access-Control-Allow-Headers", "*")
                self.end_headers()
                self.wfile.write(body)

            def do_OPTIONS(self):
                self.send_response(200)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.send_header("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS")
                self.send_header("Access-Control-Allow-Headers", "*")
                self.end_headers()

            def do_GET(self):
                parsed = urlparse(self.path)
                path = parsed.path
                if path == "/":
                    html = f"""<!DOCTYPE html>
<html>
<head>
    <meta charset="utf-8">
    <title>{device.device_name} - 虚拟大屏</title>
    <style>
        * {{ box-sizing: border-box; margin: 0; padding: 0; }}
        body {{
            background: #0d1117; color: #fff;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
            display: flex; flex-direction: column; height: 100vh; overflow: hidden;
        }}
        .header {{
            background: rgba(0,0,0,0.85); padding: 12px 24px;
            display: flex; justify-content: space-between; align-items: center;
            border-bottom: 2px solid #238636; z-index: 10;
        }}
        .header strong {{ font-size: 18px; color: #58a6ff; }}
        .header span {{ font-size: 13px; color: #8b949e; }}
        .screen {{
            flex: 1; display: flex; align-items: center; justify-content: center;
            padding: 30px; text-align: center; position: relative;
        }}
        .content-box {{
            max-width: 90%; max-height: 90%;
            display: flex; align-items: center; justify-content: center;
            white-space: pre-wrap; word-break: break-word;
        }}
        img, video {{ max-width: 100%; max-height: 80vh; object-fit: contain; }}
        .status-badge {{
            position: absolute; bottom: 20px; right: 20px;
            background: rgba(0,0,0,0.6); padding: 6px 14px; border-radius: 20px;
            font-size: 12px; color: #7ee787; border: 1px solid #238636;
        }}
    </style>
</head>
<body>
    <div class="header">
        <div>
            <strong>📺 {device.device_name} (模拟器)</strong>
            <span> &nbsp;|&nbsp; 端口: {device.port} &nbsp;|&nbsp; ID: {device.device_id} &nbsp;|&nbsp; 配对 Token: <b style="color:#f0883e">{device.pairing_token}</b></span>
        </div>
        <span id="syncTime">已连接</span>
    </div>
    <div class="screen" id="screen">
        <div class="content-box" id="contentBox"></div>
        <div class="status-badge" id="badge">● 正在播放</div>
    </div>
    <script>
        async function checkUpdate() {{
            try {{
                const res = await fetch('/api/status');
                const data = await res.json();
                document.getElementById('syncTime').textContent = '音量: ' + data.volume + '% | 状态: ' + (data.playing ? '播放中' : '已暂停');
                document.getElementById('badge').textContent = data.playing ? '● 正在播放' : '⏸ 已暂停';
                document.getElementById('badge').style.color = data.playing ? '#7ee787' : '#f85149';
                
                const cur = data.currentResource;
                const box = document.getElementById('contentBox');
                const screen = document.getElementById('screen');
                if (cur) {{
                    screen.style.background = cur.textBackgroundColor || '#0d1117';
                    box.style.color = cur.textColor || '#ffffff';
                    box.style.fontSize = (cur.textSizeSp || 48) + 'px';
                    if (cur.kind === 'TEXT') {{
                        box.innerHTML = '';
                        box.textContent = cur.content || '';
                    }} else if (cur.kind === 'IMAGE' || (cur.mimeType && cur.mimeType.startsWith('image/'))) {{
                        box.innerHTML = '<img src="' + (cur.sourceUri || cur.url || '') + '" alt="Image">';
                    }} else if (cur.kind === 'VIDEO' || (cur.mimeType && cur.mimeType.startsWith('video/'))) {{
                        box.innerHTML = '<video src="' + (cur.sourceUri || cur.url || '') + '" autoplay loop muted></video>';
                    }} else if (cur.kind === 'WEB') {{
                        if (cur.content) box.innerHTML = cur.content;
                        else box.innerHTML = '<iframe src="' + cur.sourceUri + '" style="width:80vw;height:70vh;border:0;"></iframe>';
                    }} else {{
                        box.textContent = cur.content || cur.name;
                    }}
                }}
            }} catch(e) {{}}
        }}
        setInterval(checkUpdate, 1500);
        checkUpdate();
    </script>
</body>
</html>"""
                    self.send_response(200)
                    self.send_header("Content-Type", "text/html; charset=utf-8")
                    self.send_header("Content-Length", str(len(html.encode("utf-8"))))
                    self.end_headers()
                    self.wfile.write(html.encode("utf-8"))
                    return

                if path == "/api/device":
                    self.send_json({
                        "deviceId": device.device_id,
                        "deviceName": device.device_name,
                        "version": "0.1.0",
                        "port": device.port
                    })
                    return

                if path == "/api/status":
                    self.send_json({
                        "deviceId": device.device_id,
                        "deviceName": device.device_name,
                        "currentResourceId": device.current_resource.get("id"),
                        "currentSceneId": "scene_1",
                        "currentPlaylistId": None,
                        "playing": device.playing,
                        "volume": device.volume,
                        "muted": device.muted,
                        "error": device.last_error,
                        "commandRevision": device.command_revision,
                        "currentResource": device.current_resource
                    })
                    return

                if path.startswith("/api/resources/") and path.endswith("/exists"):
                    self.send_json({"exists": False, "resourceId": None})
                    return

                self.send_json({"error": "NOT_FOUND"}, 404)

            def do_POST(self):
                parsed = urlparse(self.path)
                path = parsed.path
                content_len = int(self.headers.get('Content-Length', 0))
                body_bytes = self.rfile.read(content_len) if content_len > 0 else b''
                body_json = {}
                if body_bytes:
                    try:
                        body_json = json.loads(body_bytes.decode('utf-8'))
                    except Exception:
                        pass

                if path in ("/api/internal/sync/resource", "/api/resources/virtual"):
                    res_id = f"res_{int(time.time()*1000)}"
                    res = {
                        "id": res_id,
                        "name": body_json.get("name", "Synced Resource"),
                        "kind": body_json.get("kind", "TEXT"),
                        "content": body_json.get("content", ""),
                        "sourceUri": body_json.get("sourceUri"),
                        "textColor": body_json.get("textColor", "#ffffff"),
                        "textBackgroundColor": body_json.get("textBackgroundColor", "#1a3b30"),
                        "textSizeSp": body_json.get("textSizeSp", 48),
                        "fontFamily": body_json.get("fontFamily", "SYSTEM_SANS")
                    }
                    device.resources[res_id] = res
                    device.current_resource = res
                    print(f"[{device.device_name}] 收到新素材: {res['name']} ({res['kind']})")
                    self.send_json({"id": res_id, "exists": True})
                    return

                if path == "/api/resources/upload":
                    res_id = f"upload_{int(time.time()*1000)}"
                    res = {
                        "id": res_id,
                        "name": "Uploaded Media",
                        "kind": "IMAGE",
                        "content": f"Uploaded file ({len(body_bytes)} bytes)"
                    }
                    device.resources[res_id] = res
                    device.current_resource = res
                    self.send_json({"id": res_id, "ids": [res_id]})
                    return

                if path == "/api/control":
                    action = body_json.get("action", "")
                    res_id = body_json.get("resourceId")
                    if res_id and res_id in device.resources:
                        device.current_resource = device.resources[res_id]
                    if action == "PLAY":
                        device.playing = True
                    elif action in ("PAUSE", "STOP"):
                        device.playing = False
                    elif action == "VOLUME":
                        device.volume = body_json.get("value", device.volume)
                    elif action == "MUTE":
                        device.muted = True
                    elif action == "UNMUTE":
                        device.muted = False
                    device.command_revision += 1
                    print(f"[{device.device_name}] 执行指令: {action} (当前: {device.current_resource.get('name')})")
                    self.send_json({"success": True})
                    return

                self.send_json({"success": True})

        self.http_server = HTTPServer(("0.0.0.0", self.port), Handler)
        self.http_thread = threading.Thread(target=self.http_server.serve_forever, daemon=True)
        self.http_thread.start()
        print(f"[*] 虚拟设备已启动: {self.device_name}")
        print(f"    - 大屏画面: http://localhost:{self.port}")
        print(f"    - 配对 Token: {self.pairing_token}")

def run_udp_announcer(devices):
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_BROADCAST, 1)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    try:
        sock.bind(('', UDP_DISCOVERY_PORT))
    except Exception as e:
        print(f"[!] UDP 绑定警告: {e}")

    def announce_loop():
        while True:
            for dev in devices:
                msg = json.dumps({
                    "version": PROTOCOL_VERSION,
                    "type": "SIGNAGE",
                    "deviceId": dev.device_id,
                    "deviceName": dev.device_name,
                    "port": dev.port
                }).encode("utf-8")
                try:
                    sock.sendto(msg, ("255.255.255.255", UDP_DISCOVERY_PORT))
                    sock.sendto(msg, ("127.0.0.1", UDP_DISCOVERY_PORT))
                except Exception:
                    pass
            time.sleep(5)

    threading.Thread(target=announce_loop, daemon=True).start()

def main():
    print("=" * 60)
    print("  Local Signage 多设备联调模拟器")
    print("=" * 60)

    devices = [
        VirtualSignageDevice("sim_screen_b", "2号屏幕 (大厅看板)", 8081, "123456"),
        VirtualSignageDevice("sim_screen_c", "3号屏幕 (橱窗展示)", 8082, "123456"),
    ]

    for dev in devices:
        dev.start_http()

    run_udp_announcer(devices)

    print("-" * 60)
    print("提示:")
    print("1. 浏览器打开 http://localhost:8081 查看【2号屏幕】实时画面")
    print("2. 浏览器打开 http://localhost:8082 查看【3号屏幕】实时画面")
    print("3. 在【主控机 Web 控制台】(http://localhost:8080) -> 【设备】中配对这两台设备")
    print("4. 配对后在【发送】页自由选择目标设备，即可实现不同屏幕展示不同内容！")
    print("=" * 60)

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n模拟器已退出")

if __name__ == "__main__":
    main()
