"""Exercise OneScript HTTP and both smoke-test entry points against localhost."""
from __future__ import annotations

import argparse
from collections import Counter
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
from urllib.parse import parse_qs, quote, urlsplit

GUID = "4c401c09-752a-4c74-8d2d-4d524ec9aa84"
AGENT = "Сервер:1540"
CLUSTER = "Сервер:8541"
BASE = CLUSTER + "|База Тест"
TOKEN = "http-test+token/&?"
PERMISSION_CODE = "test-permission-code"
LAUNCH_TOKEN = "fake-launch-token"
COMMENT = "Локальный тест"

GET_CASES = {
    "Objects1C/GetAgents": ({}, []),
    "Objects1C/GetClusters": ({"agent": AGENT}, []),
    "Objects1C/GetProjectTeams": ({}, []),
    "Objects1C/GetProjects": ({}, []),
    "Objects1C/GetInfobases": ({"agent": AGENT, "cluster": CLUSTER}, []),
    "Objects1C/GetSessions": ({"cluster": CLUSTER}, []),
    "Objects1C/GetInfobaseUsers": ({"infobase": BASE, "get_id": "1", "only_ad": "0"}, [{"name": "Иван"}]),
    "Objects1C/GetInfobaseBlock": ({"infobase": BASE, "token": TOKEN}, {"SessionsLockEnabled": False}),
    "Objects1C/GetInfobaseMaintenance": ({"infobase": BASE, "token": TOKEN}, {"MaintenanceDisabled": False}),
    "audit/AIR": ({"full": "1", "infobase": BASE}, ""),
    "audit/VIR": ({"groupmarker": "2", "infobase": BASE}, ""),
    "loading1c/status": ({"guid": GUID, "token": TOKEN}, None),
    "run1c/generate": ({"infobase": BASE, "appname": "test-app", "user": "Аналитик",
                       "comment": COMMENT, "token": TOKEN}, LAUNCH_TOKEN),
}

POST_CASES = {
    "Objects1C/SetInfobaseBlock": ({
        "infobase": BASE, "SessionsLockEnabled": True, "LockScheduledJobs": False,
        "LockBeginTime": "2026-11-25T08:30:00", "LockEndTime": "2026-11-25T09:30:00",
        "SessionStartPermissionCode": PERMISSION_CODE, "LockMessage": "На обслуживании",
        "LockParameter": "test", "comment": COMMENT,
    }, True),
    "Objects1C/SetInfobaseMaintenance": ({
        "infobase": BASE, "MaintenanceDisabled": True, "MaintenanceDisabledDate": "2026-11-26T00:00:00",
        "MaintenanceDisabledNote": "Проверка", "comment": COMMENT,
    }, {"MaintenanceDisabled": True}),
    "Objects1C/TerminateProcess": ({"cluster": CLUSTER, "process": "test-process", "comment": COMMENT}, ""),
    "Objects1C/KillSession": ({"infobase": BASE, "session": 123, "timeout": 30, "comment": COMMENT}, True),
    "Objects1C/CacheReset": ({"infobase": BASE, "comment": COMMENT}, ""),
    "loading1c/new": ({
        "sourceinfobase": "SOURCE", "targetinfobase": "TARGET", "test": 1,
        "makecopy": True, "user": "Аналитик", "osname": r"DOMAIN\analyst",
    }, GUID),
}

UNLOCK = {"infobase": BASE, "SessionsLockEnabled": False, "LockScheduledJobs": False, "comment": "Проверка снятия"}


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--oscript", default="oscript")
    args = parser.parse_args()
    errors: list[str] = []
    calls: Counter[tuple[str, str]] = Counter()
    states = iter((1, 2, 0))

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args: object) -> None:
            pass

        def respond(self, result: object, *, code: int | str = 0, description: str = "") -> None:
            body = ("\ufeff" + json.dumps(
                {"code": code, "description": description, "result": result,
                 "update_date": "2026-11-25T09:43:04"}, ensure_ascii=False
            )).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self) -> None:
            self.handle_request("GET")

        def do_POST(self) -> None:
            self.handle_request("POST")

        def handle_request(self, method: str) -> None:
            url = urlsplit(self.path)
            route = url.path.removeprefix("/hs/")
            calls[method, route] += 1
            try:
                assert url.path.startswith("/hs/"), "expected root /hs/"
                assert self.headers.get("token") is None, "token must not be duplicated in a header"
                if method == "GET":
                    self.check_get(route, url.query)
                else:
                    self.check_post(route, url.query)
            except (AssertionError, KeyError, ValueError, StopIteration) as error:
                errors.append(f"{method} {route}: {error}")
                self.respond(None, code=99, description="mock assertion failed")

        def check_get(self, route: str, query: str) -> None:
            actual = parse_qs(query, keep_blank_values=True)
            expected, result = GET_CASES[route]
            if route == "Objects1C/GetInfobaseBlock" and not actual.get("token"):
                self.respond(None, code=5, description="Не указан параметр 'token'")
                return
            if route == "Objects1C/GetInfobaseBlock" and actual.get("infobase") == ["REDIRECT"]:
                assert actual == {"infobase": ["REDIRECT"], "token": [TOKEN]}, "redirect query"
                self.send_response(302)
                self.send_header("Location", "/hs/unexpected-redirect")
                self.send_header("Content-Length", "0")
                self.end_headers()
                return
            assert actual == {key: [value] for key, value in expected.items()}, "GET parameters"
            if route == "loading1c/status":
                self.respond(next(states), code="00.00")
            elif route == "Objects1C/GetInfobaseBlock":
                self.respond(result, code="00:00")
            else:
                self.respond(result)

        def check_post(self, route: str, query: str) -> None:
            assert not query, "POST URL without query"
            assert self.headers.get("Content-Type") == "application/json; charset=utf-8", "content type"
            body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            assert body.pop("token", None) == TOKEN, "token in JSON"
            expected, result = POST_CASES[route]
            if route == "Objects1C/SetInfobaseBlock" and calls["POST", route] == 2:
                expected = UNLOCK
            assert body == expected, "POST body"
            assert all(type(body[key]) is type(value) for key, value in expected.items()), "JSON field types"
            self.respond(result, code="00.00" if route == "loading1c/new" else 0)

    here = Path(__file__).resolve().parent
    examples = here.parents[2] / "Скрипты" / "Примеры"
    with tempfile.TemporaryDirectory(prefix="mechel-agent-") as folder:
        agent = Path(folder)
        library = agent / "lib" / "mechel2"
        for subdir in ("api", "src"):
            shutil.copytree(here.parent / subdir, library / subdir)
        installed_library = str(library.resolve())
        installed_script = agent / "api" / "ПроверитьАПИ.os"
        installed_script.parent.mkdir()
        shutil.copy2(examples / "НаАгенте" / installed_script.name, installed_script)

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            worker = threading.Thread(target=server.serve_forever, daemon=True)
            worker.start()
            try:
                endpoint = f"http://127.0.0.1:{server.server_port}/hs/"
                env = {**os.environ, "MECHEL_API_URL": endpoint,
                       "MECHEL_API_TOKEN": TOKEN, "MECHEL_TEST_INFOBASE": BASE}

                def run(script: Path, *extra: str) -> subprocess.CompletedProcess[str]:
                    return subprocess.run(
                        [args.oscript, "-encoding=utf-8", str(script), *extra],
                        env=env, capture_output=True, text=True, encoding="utf-8", errors="replace", timeout=40,
                    )

                transport = run(here / "ТестHTTP.os", endpoint)
                smoke_runs = () if transport.returncode else (
                    run(examples / "ПроверитьАПИ.os"), run(installed_script),
                )
            finally:
                server.shutdown()
                worker.join()

    results = (transport, *smoke_runs)
    output = "".join(result.stdout + result.stderr for result in results)
    for secret in (TOKEN, PERMISSION_CODE, LAUNCH_TOKEN):
        if secret in output or quote(secret, safe="").lower() in output.lower():
            print("FAIL: test secret leaked to the console")
            return 1
    print(output, end="")
    expected_calls = Counter({("GET", route): 1 for route in GET_CASES})
    expected_calls.update({("POST", route): 1 for route in POST_CASES})
    expected_calls.update({("GET", "Objects1C/GetAgents"): 2, ("GET", "Objects1C/GetInfobaseBlock"): 3,
                           ("GET", "loading1c/status"): 2, ("POST", "Objects1C/SetInfobaseBlock"): 1})
    if (len(smoke_runs) != 2 or any(result.returncode for result in results) or errors or calls != expected_calls
            or any("Чтение параметров блокировки выполнено" not in result.stdout for result in smoke_runs)
            or os.path.normcase(installed_library) not in os.path.normcase(smoke_runs[1].stdout)):
        print(f"FAIL: exit={[result.returncode for result in results]}; calls={sum(calls.values())}; errors={errors}")
        print(f"Missing requests: {expected_calls - calls}; extra requests: {calls - expected_calls}")
        return 1
    print(f"PASS: {len(calls)} API endpoints, {sum(calls.values())} real HTTP requests, both smoke-test entry points.")
    return 0


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())
