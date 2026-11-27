"""Run the deployed OneScript entry point against a temporary local HTTP server."""

import argparse
import json
import os
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import threading
from urllib.parse import parse_qs, quote, urlsplit

GUID = "61a78036-5acb-4281-b0c1-4d70b8a6bc6b"
FAILED_GUID = "75ad092f-15de-4928-a646-abe9d9fd1ea0"
TOKEN = "local+restore/token&?"
SOURCE = "Кластер:6541|Источник"
TARGET = "Кластер:6541|Приёмник с пробелом"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--oscript", default="oscript")
    args = parser.parse_args()
    calls = []

    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def reply(self, code, result, description=""):
            body = json.dumps({"code": code, "description": description, "result": result},
                              ensure_ascii=False).encode("utf-8")
            self.send_response(200)
            self.send_header("Content-Type", "application/json; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            data = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
            calls.append(("POST", self.path, data))
            if data["targetinfobase"] == "Отклонённая ИБ":
                self.reply("00.04", "", "Заявка отклонена тестовым API")
            else:
                self.reply("00.00", GUID)

        def do_GET(self):
            request = urlsplit(self.path)
            params = parse_qs(request.query)
            calls.append(("GET", request.path, params))
            self.reply("00.00", -1 if params["guid"] == [FAILED_GUID] else 0)

    here = Path(__file__).resolve().parent
    library = here.parents[2] / "Библиотека" / "mechel"
    with tempfile.TemporaryDirectory(prefix="mechel-restore-") as folder:
        agent = Path(folder)
        for part in ("api", "src"):
            shutil.copytree(library / part, agent / "lib" / "mechel2" / part)
        script = agent / "api" / "ВосстановитьИБ.os"
        script.parent.mkdir()
        shutil.copy2(here.parent / script.name, script)
        shutil.copy2(here.parent / "ЗащитаПриемника.os", script.parent)

        with ThreadingHTTPServer(("127.0.0.1", 0), Handler) as server:
            thread = threading.Thread(target=server.serve_forever, daemon=True)
            thread.start()
            env = {
                "MECHEL_API_URL": f"http://127.0.0.1:{server.server_port}/hs/",
                "MECHEL_API_TOKEN": TOKEN,
                "MECHEL_RESTORE_MODE": "test",
                "MECHEL_RESTORE_SOURCE": SOURCE,
                "MECHEL_RESTORE_TARGET": TARGET,
                "MECHEL_RESTORE_MAKE_COPY": "0",
                "MECHEL_RESTORE_USER": "",
                "MECHEL_RESTORE_OSNAME": "",
                "MECHEL_RESTORE_GUID": "",
                "MECHEL_RESTORE_GUID_FILE": "mechel-restore/42/restore-guid.txt",
                "MECHEL_RESTORE_WAIT_MINUTES": "120",
                "JOB_NAME": "erp/prepare-db",
                "BUILD_NUMBER": "42",
            }

            def run(command, expected_message, *, success=True, **overrides):
                completed = subprocess.run(
                    [args.oscript, "-encoding=utf-8", str(script), command],
                    cwd=agent, env={**os.environ, **env, **overrides}, capture_output=True,
                    encoding="utf-8", errors="replace", timeout=20,
                )
                output = completed.stdout + completed.stderr
                assert TOKEN not in output and quote(TOKEN, safe="") not in output
                assert (completed.returncode == 0) == success, output
                assert expected_message in output, output
                return output

            try:
                run("check", "Проверены адреса восстановления", MECHEL_API_TOKEN="", MECHEL_RESTORE_SOURCE="ERP_YU")
                for name in ("ERP_KGOK_DAILY", "ERP_YU_DAILY", "ERP_YKUZ_DAILY", "ERP_YU", "ERP_KGOK", "ERP_YKUZ"):
                    run("check", f"База {name} защищена", success=False,
                        MECHEL_RESTORE_TARGET=f"server:6541| {name.lower()} ")
                    run("create", f"База {name} защищена", success=False, MECHEL_RESTORE_TARGET=name)
                run("check", "формате СЕРВЕР:ПОРТ", success=False, MECHEL_RESTORE_TARGET="ERP_DEV")
                assert not (agent / "mechel-restore").exists()
                output = run("create", f"Заявка принята. GUID: {GUID}", MECHEL_LOG_LEVEL="INFO")
                assert "[DEBUG]" not in output
                guid_file = agent / env["MECHEL_RESTORE_GUID_FILE"]
                guid = guid_file.read_text(encoding="ascii").strip()
                assert guid == GUID and not Path(str(guid_file) + ".pending").exists()
                output = run("wait", "завершена со статусом 0", MECHEL_RESTORE_GUID=guid, MECHEL_LOG_LEVEL="DEBUG")
                assert "[DEBUG]" in output and "GET loading1c/status" in output
                run("create", "уже была попытка отправки", success=False)
                run("create", "GUID уже задан", success=False, MECHEL_RESTORE_GUID=guid)
                run("wait", "с ошибкой", success=False, MECHEL_RESTORE_GUID=FAILED_GUID)
                run("wait", "положительным целым числом", success=False,
                    MECHEL_RESTORE_GUID=guid, MECHEL_RESTORE_WAIT_MINUTES="0")

                run("create", "Заявка принята", MECHEL_RESTORE_MODE="restore",
                    MECHEL_RESTORE_MAKE_COPY="1", MECHEL_RESTORE_USER="Аналитик",
                    MECHEL_RESTORE_OSNAME=r"DOMAIN\analyst", MECHEL_RESTORE_GUID_FILE="real/restore-guid.txt")

                rejected = {"MECHEL_RESTORE_TARGET": "Отклонённая ИБ",
                            "MECHEL_RESTORE_GUID_FILE": "rejected/restore-guid.txt"}
                run("create", "Заявка отклонена тестовым API", success=False, **rejected)
                assert (agent / "rejected/restore-guid.txt.pending").is_file()
                assert not (agent / "rejected/restore-guid.txt").exists()
                run("create", "уже была попытка отправки", success=False, **rejected)

                run("create", "должны быть разными", success=False,
                    MECHEL_RESTORE_TARGET=SOURCE.lower(), MECHEL_RESTORE_GUID_FILE="invalid/restore-guid.txt")
                run("create", "укажите MECHEL_RESTORE_USER", success=False,
                    MECHEL_RESTORE_OSNAME=r"DOMAIN\analyst", MECHEL_RESTORE_GUID_FILE="invalid/restore-guid.txt")
                run("create", "MECHEL_API_TOKEN", success=False, MECHEL_API_TOKEN="")
            finally:
                server.shutdown()
                thread.join()

    default = {"token": TOKEN, "sourceinfobase": SOURCE, "targetinfobase": TARGET,
               "comment": "Jenkins: job=erp/prepare-db #42", "test": 1, "makecopy": False}
    assert calls == [
        ("POST", "/hs/loading1c/new", default),
        ("GET", "/hs/loading1c/status", {"guid": [GUID], "token": [TOKEN]}),
        ("GET", "/hs/loading1c/status", {"guid": [FAILED_GUID], "token": [TOKEN]}),
        ("POST", "/hs/loading1c/new",
         {**default, "test": 0, "makecopy": True, "user": "Аналитик", "osname": r"DOMAIN\analyst"}),
        ("POST", "/hs/loading1c/new", {**default, "targetinfobase": "Отклонённая ИБ"}),
    ], "Unexpected request order or payload"
    for method, _, data in calls:
        if method == "POST":
            assert type(data["test"]) is int and type(data["makecopy"]) is bool
    print("PASS: protected receivers, test/restore payloads, wait-only mode, GUID file, failure and repeat protection")


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    main()
