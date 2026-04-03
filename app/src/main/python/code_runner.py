import contextlib
import io
import json
import os
from pathlib import Path
import traceback

from package_manager import activate_runtime_packages


def read_text_file(file_path):
    path = Path(file_path)
    if not path.exists():
        path.parent.mkdir(parents=True, exist_ok=True)
        path.touch()
        return ""

    return path.read_text(encoding="utf-8", errors="replace")


def write_text_file(file_path, content):
    path = Path(file_path)
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text("" if content is None else str(content), encoding="utf-8")
    return str(path)


def run_python_code(file_path, code, packages_root=None):
    path = Path(file_path)
    buffer = io.StringIO()
    namespace = {
        "__name__": "__main__",
        "__file__": str(path),
    }
    previous_cwd = Path.cwd()
    success = True

    try:
        os.chdir(path.parent)
        if packages_root:
            activate_runtime_packages(packages_root)
        with contextlib.redirect_stdout(buffer), contextlib.redirect_stderr(buffer):
            exec(compile(code, str(path), "exec"), namespace)
    except Exception:
        success = False
        with contextlib.redirect_stdout(buffer), contextlib.redirect_stderr(buffer):
            traceback.print_exc()
    finally:
        os.chdir(previous_cwd)

    output = buffer.getvalue().strip()
    if not output:
        output = "Execucao finalizada sem saida." if success else "Execucao encerrada com erro."

    return json.dumps(
        {
            "success": success,
            "output": output,
        }
    )
