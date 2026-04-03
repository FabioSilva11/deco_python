import csv
from datetime import datetime
import json
from pathlib import Path
import re
import shutil
import tempfile
import urllib.request
import zipfile


PACKAGE_RE = re.compile(r"^\s*([A-Za-z0-9_.-]+)\s*(?:==\s*([A-Za-z0-9_.-]+)\s*)?$")
NORMALIZE_RE = re.compile(r"[-_.]+")


def list_runtime_packages(root_path):
    root = _ensure_root(root_path)
    state = _load_state(root)
    packages = sorted(state["packages"], key=lambda item: item["name"].lower())
    return json.dumps(packages)


def resolve_runtime_package(requirement):
    package_name, requested_version = _parse_requirement(requirement)
    metadata = _fetch_package_metadata(package_name, requested_version)
    resolved_name = metadata["info"]["name"]
    resolved_version = metadata["info"]["version"]
    resolved_requirement = (
        f"{resolved_name}=={requested_version}"
        if requested_version
        else resolved_name
    )

    return json.dumps(
        {
            "name": resolved_name,
            "version": resolved_version,
            "resolved_requirement": resolved_requirement,
        }
    )


def install_runtime_package(root_path, requirement):
    root = _ensure_root(root_path)
    resolution = json.loads(resolve_runtime_package(requirement))
    metadata = _fetch_package_metadata(
        resolution["name"],
        resolution["version"] if "==" in resolution["resolved_requirement"] else None,
    )
    wheel = _pick_wheel(metadata)
    site_packages = root / "site-packages"

    state = _load_state(root)
    existing = _find_state_entry(state, resolution["name"])
    if existing:
        _uninstall_entry(site_packages, existing)
        state["packages"] = [
            item for item in state["packages"]
            if _canonical_name(item["name"]) != _canonical_name(resolution["name"])
        ]

    with tempfile.TemporaryDirectory() as temp_dir:
        wheel_path = Path(temp_dir) / wheel["filename"]
        urllib.request.urlretrieve(wheel["url"], wheel_path)

        with zipfile.ZipFile(wheel_path) as archive:
            archive.extractall(site_packages)
            dist_info_dir = _find_dist_info_dir(archive.namelist())

    entry = {
        "name": metadata["info"]["name"],
        "version": metadata["info"]["version"],
        "dist_info_dir": dist_info_dir,
        "installed_at": datetime.utcnow().isoformat() + "Z",
    }
    state["packages"].append(entry)
    _save_state(root, state)
    return json.dumps({"name": entry["name"], "version": entry["version"]})


def uninstall_runtime_package(root_path, package_name):
    root = _ensure_root(root_path)
    site_packages = root / "site-packages"
    state = _load_state(root)
    entry = _find_state_entry(state, package_name)
    if not entry:
        raise FileNotFoundError(f"A biblioteca '{package_name}' nao foi encontrada.")

    _uninstall_entry(site_packages, entry)
    state["packages"] = [
        item for item in state["packages"]
        if _canonical_name(item["name"]) != _canonical_name(package_name)
    ]
    _save_state(root, state)


def activate_runtime_packages(root_path):
    root = _ensure_root(root_path)
    site_packages = root / "site-packages"
    import sys

    site_path = str(site_packages)
    if site_path not in sys.path:
        sys.path.insert(0, site_path)
    return site_path


def _ensure_root(root_path):
    root = Path(root_path)
    (root / "site-packages").mkdir(parents=True, exist_ok=True)
    return root


def _state_file(root):
    return root / "runtime-packages.json"


def _load_state(root):
    file_path = _state_file(root)
    if not file_path.exists():
        return {"packages": []}

    return json.loads(file_path.read_text(encoding="utf-8"))


def _save_state(root, state):
    _state_file(root).write_text(
        json.dumps(state, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )


def _parse_requirement(requirement):
    match = PACKAGE_RE.match(requirement or "")
    if not match:
        raise ValueError("Use um nome como requests ou requests==2.32.3.")

    return match.group(1), match.group(2)


def _fetch_package_metadata(package_name, version):
    normalized_name = _canonical_name(package_name)
    if version:
        url = f"https://pypi.org/pypi/{normalized_name}/{version}/json"
    else:
        url = f"https://pypi.org/pypi/{normalized_name}/json"

    try:
        with urllib.request.urlopen(url, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except Exception as exc:
        raise RuntimeError(f"Nao foi possivel consultar o PyPI: {exc}") from exc


def _pick_wheel(metadata):
    urls = metadata.get("urls") or []
    wheels = [
        item for item in urls
        if item.get("packagetype") == "bdist_wheel"
        and item.get("filename", "").endswith(".whl")
    ]

    preferred = [
        item for item in wheels
        if "none-any.whl" in item.get("filename", "")
        and ("py3" in item.get("filename", "") or "py2.py3" in item.get("filename", ""))
    ]

    if not preferred:
        raise RuntimeError(
            "Essa biblioteca nao possui wheel Python puro compativel para instalacao no aparelho."
        )

    return preferred[0]


def _find_dist_info_dir(names):
    for name in names:
        parts = name.split("/")
        if parts and parts[0].endswith(".dist-info"):
            return parts[0]
    raise RuntimeError("Nao foi possivel identificar os metadados da biblioteca instalada.")


def _find_state_entry(state, package_name):
    canonical = _canonical_name(package_name)
    for item in state["packages"]:
        if _canonical_name(item["name"]) == canonical:
            return item
    return None


def _canonical_name(name):
    return NORMALIZE_RE.sub("-", name).lower()


def _uninstall_entry(site_packages, entry):
    dist_info = site_packages / entry["dist_info_dir"]
    record_file = dist_info / "RECORD"
    if not record_file.exists():
        shutil.rmtree(dist_info, ignore_errors=True)
        return

    with record_file.open("r", encoding="utf-8", newline="") as handle:
        for row in csv.reader(handle):
            if not row:
                continue
            relative_path = row[0]
            target = (site_packages / relative_path).resolve()
            if site_packages.resolve() not in target.parents and target != site_packages.resolve():
                continue
            if target.is_file():
                target.unlink(missing_ok=True)

    shutil.rmtree(dist_info, ignore_errors=True)
