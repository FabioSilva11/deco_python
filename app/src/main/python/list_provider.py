from datetime import datetime
import json
from pathlib import Path
import re
import shutil


INVALID_CHARS = re.compile(r'[<>:"/\\|?*]')


def ensure_base_dir(base_path):
    path = Path(base_path)
    path.mkdir(parents=True, exist_ok=True)
    return str(path)


def list_directories(base_path):
    path = Path(base_path)
    if not path.exists():
        return []

    return sorted(
        [item.name for item in path.iterdir() if item.is_dir()],
        key=str.lower,
    )


def list_entries(base_path):
    path = Path(base_path)
    if not path.exists():
        return "[]"

    entries = [
        {"name": item.name, "is_dir": item.is_dir()}
        for item in sorted(
            path.iterdir(),
            key=lambda item: (not item.is_dir(), item.name.lower()),
        )
    ]
    return json.dumps(entries)


def create_folder(base_path, folder_name):
    base_dir = Path(ensure_base_dir(base_path))
    cleaned_name = _clean_name(folder_name)

    if not cleaned_name:
        cleaned_name = f"NovaPasta_{datetime.now().strftime('%Y%m%d_%H%M%S')}"

    target = base_dir / cleaned_name
    if target.exists():
        raise FileExistsError(f"A pasta '{cleaned_name}' ja existe.")

    target.mkdir()
    return str(target)


def create_file(base_path, file_name):
    base_dir = Path(ensure_base_dir(base_path))
    cleaned_name = _clean_name(file_name)

    if not cleaned_name:
        cleaned_name = f"NovoArquivo_{datetime.now().strftime('%Y%m%d_%H%M%S')}.txt"

    target = base_dir / cleaned_name
    if target.exists():
        raise FileExistsError(f"O arquivo '{cleaned_name}' ja existe.")

    target.touch()
    return str(target)


def rename_folder(base_path, old_name, new_name):
    return rename_entry(base_path, old_name, new_name)


def delete_folder(base_path, folder_name):
    return delete_entry(base_path, folder_name)


def rename_entry(base_path, old_name, new_name):
    base_dir = Path(ensure_base_dir(base_path))
    source = _resolve_entry(base_dir, old_name)
    cleaned_name = _clean_name(new_name)

    if not cleaned_name:
        raise ValueError("Digite um nome para o item.")

    target = base_dir / cleaned_name
    if target.exists() and target != source:
        raise FileExistsError(f"O item '{cleaned_name}' ja existe.")

    source.rename(target)
    return str(target)


def delete_entry(base_path, entry_name):
    base_dir = Path(ensure_base_dir(base_path))
    target = _resolve_entry(base_dir, entry_name)
    if target.is_dir():
        shutil.rmtree(target)
    else:
        target.unlink()


def _clean_name(folder_name):
    if folder_name is None:
        return ""

    cleaned = INVALID_CHARS.sub("_", str(folder_name)).strip().strip(".")
    return cleaned


def _resolve_directory(base_dir, folder_name):
    target = _resolve_entry(base_dir, folder_name)
    if not target.is_dir():
        raise NotADirectoryError(f"O item '{target.name}' nao e uma pasta.")

    return target


def _resolve_entry(base_dir, name):
    cleaned_name = _clean_name(name)
    if not cleaned_name:
        raise ValueError("Nome de item invalido.")

    target = (base_dir / cleaned_name).resolve()
    if target.parent != base_dir.resolve() or not target.exists():
        raise FileNotFoundError(f"O item '{cleaned_name}' nao foi encontrado.")

    return target
