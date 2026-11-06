#!/usr/bin/env python3
"""本地 Mock 正方教务：登录页 → 公钥 → 验证码 → 登录 → 课表。

用途：桌面端 P2 的端到端验收。真实学校环境未验证（方案 B）。
    python tools/mock-jwxt.py --port 18080
    java -jar app/build/libs/pocket-xiaoli-desktop.jar \
      --demo=login --login-url=http://127.0.0.1:18080/jwglxt \
      --login-user=20260001 --login-pass=secret --login-captcha=abcd --login-auto
"""
import argparse
import base64
import json
import struct
import zlib
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

try:
    from cryptography.hazmat.primitives.asymmetric import rsa
except Exception as exc:  # pragma: no cover
    raise SystemExit("需要 cryptography：pip install cryptography") from exc

_key = rsa.generate_private_key(public_exponent=65537, key_size=1024)
_numbers = _key.public_key().public_numbers()
MODULUS = base64.b64encode(_numbers.n.to_bytes(128, "big")).decode()

LOGIN_HTML = """<!doctype html><html><head><meta charset="utf-8"><title>统一身份认证</title></head>
<body><form id="loginForm" action="/xtgl/login_slogin.html" method="post">
<input type="hidden" id="csrftoken" name="csrftoken" value="mock-token">
<input id="yhm" name="yhm"><input id="mm" name="mm" type="password">
<input id="yzm" name="yzm"><button type="submit">登录</button>
</form></body></html>"""

SCHEDULE = {
    "zc": 9,
    "totalweek": 20,
    "kbList": [
        {"kcmc": "高等数学", "xm": "张三", "cdmc": "东九楼D101",
         "xqj": "1", "jcs": "1-2节", "zcd": "1-16周"},
        {"kcmc": "大学英语", "xm": "李四", "cdmc": "教五楼B301",
         "xqj": "3", "jcs": "3-4节", "zcd": "1-16周"},
        {"kcmc": "大学物理", "xm": "王五", "cdmc": "南一楼201",
         "xqj": "5", "jcs": "5-6节", "zcd": "1-8周(单)"},
    ],
}

GRADE_TERMS_HTML = """<!doctype html><html><head><meta charset="utf-8"></head><body>
<script>var xnxq = [{"xnm":"2026","xqm":"3"},{"xnm":"2026","xqm":"12"},{"xnm":"2025","xqm":"12"}];</script>
</body></html>"""

GRADES = {
    "items": [
        {"kcmc": "高等数学", "xf": "4", "cj": "92", "jd": "4.0",
         "pscj": "90", "kscj": "94", "qzcj": "91", "ksxzmc": "正常考试"},
        {"kcmc": "大学英语", "xf": "3", "cj": "85", "jd": "3.5",
         "pscj": "88", "kscj": "82", "ksxzmc": "正常考试"},
        {"kcmc": "大学物理", "xf": "3", "cj": "58", "jd": "0",
         "pscj": "60", "kscj": "56", "ksxzmc": "补考"},
    ],
}


def make_png(width: int = 120, height: int = 40) -> bytes:
    raw = bytearray()
    for y in range(height):
        raw.append(0)
        for x in range(width):
            raw.extend(((x * 2 + y) % 256, (x + y * 3) % 256, 180))
    compressed = zlib.compress(bytes(raw), 9)
    def chunk(tag: bytes, data: bytes) -> bytes:
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))
    ihdr = struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", compressed) + chunk(b"IEND", b""))


CAPTCHA_PNG = make_png()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):
        print("[mock-jwxt]", fmt % args, flush=True)

    def _send(self, code: int, body: bytes, content_type: str):
        self.send_response(code)
        self.send_header("Content-Type", content_type)
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        path = self.path
        if "login_getPublicKey" in path:
            body = json.dumps({"modulus": MODULUS, "exponent": "10001"}).encode()
            self._send(200, body, "application/json")
        elif "login_getCaptcha" in path:
            self._send(200, CAPTCHA_PNG, "image/png")
        elif "login_slogin" in path:
            self._send(200, LOGIN_HTML.encode("utf-8"), "text/html; charset=utf-8")
        elif "cjcx_cxDgXscj" in path:
            self._send(200, GRADE_TERMS_HTML.encode("utf-8"), "text/html; charset=utf-8")
        else:
            self._send(404, b"not found", "text/plain")

    def do_POST(self):
        length = int(self.headers.get("Content-Length") or 0)
        body = self.rfile.read(length).decode("utf-8", "replace") if length else ""
        if "login_slogin" in self.path:
            print("[mock-jwxt] login form:", body, flush=True)
            self._send(200, b'{"result":"success"}', "application/json")
        elif "xskbcx_cxXsgrkb" in self.path:
            print("[mock-jwxt] schedule form:", body, flush=True)
            self._send(200, json.dumps(SCHEDULE).encode("utf-8"), "application/json")
        elif "cjcx_cxDgXscj" in self.path:
            print("[mock-jwxt] grades form:", body, flush=True)
            self._send(200, json.dumps(GRADES).encode("utf-8"), "application/json")
        else:
            self._send(404, b"not found", "text/plain")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--port", type=int, default=18080)
    args = parser.parse_args()
    server = ThreadingHTTPServer(("127.0.0.1", args.port), Handler)
    print(f"[mock-jwxt] listening on http://127.0.0.1:{args.port}/jwglxt", flush=True)
    server.serve_forever()


if __name__ == "__main__":
    main()
