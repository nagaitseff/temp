"""Local HTTP integration test. Python 3.10+, OneScript 2.1; no corporate network."""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import threading
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import parse_qs, quote, urlsplit

GUID = "4c401c09-752a-4c74-8d2d-4d524ec9aa84"
BASE = "Сервер:8541|База Тест"
TOKEN = "http-test+token/&?"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--oscript", default="oscript")
    args = parser.parse_args()
    errors: list[str] = []
    calls: list[str] = []
    states = iter((1, 2, 0))

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args: object) -> None:
            pass

        def respond(self, result: object, *, code: int | str = 0, description: str = "") -> None:
            body = ("\ufeff" + json.dumps(
                {"code": code, "description": description, "result": result,
                 "update_date": "2026-11-18T09:43:04"}, ensure_ascii=False
            )).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def handle_request(self, method: str) -> None:
            parsed = urlsplit(self.path)
            calls.append(method + " " + parsed.path)
            try:
                if method == "GET" and parsed.path == "/hs/Objects1C/GetAgents":
                    assert not parsed.query and self.headers.get("token") is None, "agents without token"
                    self.respond([])
                elif method == "GET" and parsed.path == "/hs/Objects1C/GetInfobaseUsers":
                    assert parse_qs(parsed.query) == {
                        "infobase": [BASE], "get_id": ["1"], "only_ad": ["0"]
                    }, "GET query parameters"
                    assert self.headers.get("token") is None, "token-free method"
                    self.respond([{"name": "Иван"}])
                elif method == "GET" and parsed.path == "/hs/Objects1C/GetInfobaseBlock":
                    query = parse_qs(parsed.query)
                    if not query.get("token"):
                        self.respond(None, code=5, description="Не указан параметр 'token'")
                        return
                    assert query == {"infobase": [BASE], "token": [TOKEN]}, "block query and token encoding"
                    assert self.headers.get("token") is None, "no duplicate token header"
                    self.respond({"SessionsLockEnabled": False})
                elif method == "POST":
                    assert not parsed.query, "no token in POST URL"
                    assert self.headers.get("token") is None, "no token header"
                    assert self.headers.get("Content-Type") == "application/json; charset=utf-8", "content type"
                    body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
                    assert body.pop("token", None) == TOKEN, "token in JSON"
                    if parsed.path == "/hs/Objects1C/SetInfobaseBlock":
                        assert body == {
                            "infobase": BASE, "SessionsLockEnabled": True, "LockScheduledJobs": False
                        }, "block body"
                        self.respond(True)
                    elif parsed.path == "/hs/loading1c/new":
                        assert body == {
                            "sourceinfobase": "SOURCE", "targetinfobase": "TARGET", "test": 1,
                            "makecopy": True, "user": "Аналитик", "osname": r"DOMAIN\analyst",
                        }, "restore body"
                        assert isinstance(body["makecopy"], bool), "makecopy is a JSON boolean"
                        self.respond(GUID, code="00.00")
                    else:
                        raise AssertionError("unexpected POST")
                elif method == "GET" and parsed.path == "/hs/loading1c/status":
                    assert parse_qs(parsed.query) == {"guid": [GUID], "token": [TOKEN]}, "status GUID and token"
                    assert self.headers.get("token") is None, "no status token header"
                    self.respond(next(states), code="00.00")
                elif method == "GET" and parsed.path == "/hs/Objects1C/GetProjects":
                    self.send_response(302)
                    self.send_header("Location", "/hs/forbidden")
                    self.send_header("Content-Length", "0")
                    self.end_headers()
                else:
                    raise AssertionError("unexpected request / followed redirect")
            except Exception as exc:
                errors.append(str(exc))
                body = b'{"code":99,"description":"mock assertion failed","result":null}'
                self.send_response(200)
                self.send_header("Content-Length", str(len(body)))
                self.end_headers()
                self.wfile.write(body)

        def do_GET(self) -> None:
            self.handle_request("GET")

        def do_POST(self) -> None:
            self.handle_request("POST")

    with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
        worker = threading.Thread(target=server.serve_forever, daemon=True)
        worker.start()
        try:
            endpoint = f"http://127.0.0.1:{server.server_port}/hs/"
            transport = subprocess.run(
                [args.oscript, "-encoding=utf-8", str(Path(__file__).with_name("ТестHTTP.os")),
                 endpoint],
                capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=40,
            )
            smoke = None
            if transport.returncode == 0:
                script = Path(__file__).resolve().parents[3] / "Скрипты" / "Примеры" / "ПроверитьАПИ.os"
                smoke = subprocess.run(
                    [args.oscript, "-encoding=utf-8", str(script)],
                    env={**os.environ, "MECHEL_API_URL": endpoint,
                         "MECHEL_API_TOKEN": TOKEN, "MECHEL_TEST_INFOBASE": BASE},
                    capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=20,
                )
        finally:
            server.shutdown()
            worker.join()
    output = transport.stdout + transport.stderr
    if smoke is not None:
        output += smoke.stdout + smoke.stderr
    if TOKEN in output or quote(TOKEN, safe="").lower() in output.lower():
        print("FAIL: test token leaked to the console")
        return 1
    print(output, end="")
    if (transport.returncode or smoke is None or smoke.returncode or errors or len(calls) != 10
            or "Чтение параметров блокировки выполнено" not in smoke.stdout):
        print(f"FAIL: transport_exit={transport.returncode}; calls={len(calls)}; mock_errors={errors}")
        return 1
    print(f"PASS: mock server verified {len(calls)} real HTTP requests on 127.0.0.1.")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
