"""Fail-closed static security checks for the unified inbox.

This is intentionally conservative: it scans source (not docs) for prohibited
collection mechanisms, verifies the Android manifest, and checks the release
cleartext policy. It is not a substitute for human review.
"""

from __future__ import annotations

import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SKIP_DIRS = {
    ".git",
    ".gradle",
    ".idea",
    ".pnpm-store",
    ".tmp",
    ".tools",
    ".venv",
    ".wheels",
    "build",
    "dist",
    "node_modules",
    "__pycache__",
}

failures: list[str] = []
checks = 0


def check(condition: bool, message: str) -> None:
    global checks
    checks += 1
    if not condition:
        failures.append(message)


def walk(*parts: str, suffixes: set[str] | None = None):
    base = ROOT.joinpath(*parts)
    if not base.exists():
        return
    for path in base.rglob("*"):
        if not path.is_file():
            continue
        if any(part in SKIP_DIRS for part in path.relative_to(ROOT).parts):
            continue
        if suffixes is not None and path.suffix not in suffixes:
            continue
        yield path


def text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def main() -> int:
    manifest_path = ROOT / "android" / "app" / "src" / "main" / "AndroidManifest.xml"
    check(manifest_path.exists(), "Android manifest is missing")
    if manifest_path.exists():
        ns = "{http://schemas.android.com/apk/res/android}"
        manifest = ET.parse(manifest_path).getroot()
        permissions = {
            element.get(f"{ns}name", "")
            for element in manifest.findall("uses-permission")
        }
        expected = {
            "android.permission.INTERNET",
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS",
        }
        check(
            permissions == expected,
            f"unexpected Android permissions: {sorted(permissions - expected)} "
            f"missing: {sorted(expected - permissions)}",
        )
        check(
            not any("ACCESSIBILITY" in permission for permission in permissions),
            "Android manifest declares an accessibility permission",
        )
        check(
            "android.permission.QUERY_ALL_PACKAGES" not in permissions,
            "Android manifest declares QUERY_ALL_PACKAGES",
        )
        check(
            "android.permission.SYSTEM_ALERT_WINDOW" not in permissions,
            "Android manifest declares SYSTEM_ALERT_WINDOW",
        )
        application = manifest.find("application")
        check(application is not None, "Android manifest has no application element")
        if application is not None:
            check(
                application.get(f"{ns}usesCleartextTraffic") == "false",
                "release manifest must set usesCleartextTraffic=false",
            )
            check(
                application.get(f"{ns}allowBackup") == "false",
                "allowBackup must be false so the token is not backed up",
            )
        services = application.findall("service") if application is not None else []
        listener = [
            service
            for service in services
            if service.get(f"{ns}permission")
            == "android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        ]
        check(len(listener) == 1, "expected exactly one NotificationListenerService")
        if listener:
            check(
                listener[0].get(f"{ns}exported") == "true",
                "NotificationListenerService must be exported for the system to bind it",
            )

    service_file = (
        ROOT
        / "android/app/src/main/java/com/local/unifiedinbox/collector/InboxNotificationListenerService.kt"
    )
    check(service_file.exists(), "NotificationListenerService implementation is missing")
    if service_file.exists():
        check(
            "NotificationListenerService()" in text(service_file),
            "collector must extend NotificationListenerService",
        )

    main_network = ROOT / "android/app/src/main/res/xml/network_security_config.xml"
    check(main_network.exists(), "release network security config is missing")
    if main_network.exists():
        check(
            'cleartextTrafficPermitted="false"' in text(main_network),
            "release network security config must forbid cleartext",
        )

    android_build = ROOT / "android/app/build.gradle.kts"
    if android_build.exists():
        build_text = text(android_build).lower()
        for forbidden in ("xposed", "frida", "accessibility", "onebot", "napcat", "wechaty", "itchat"):
            check(forbidden not in build_text, f"forbidden Android dependency keyword: {forbidden}")

    prohibited_android = re.compile(
        r"(?i)(AccessibilityService|UiAutomation|Xposed|LSPosed|VirtualXposed|"
        r"frida|Magisk|OneBot|NapCat|wechaty|itchat|getInstalledPackages|getRunningTasks)"
    )
    for path in walk("android", "app", "src", "main", suffixes={".kt", ".xml", ".java"}):
        content = text(path)
        match = prohibited_android.search(content)
        check(not match, f"prohibited Android mechanism '{match.group(0) if match else ''}' in {path}")

    tencent_refs: list[Path] = []
    for path in walk("android", "app", "src", "main", suffixes={".kt", ".xml", ".java"}):
        if "com.tencent.mm" in text(path) or "com.tencent.mobileqq" in text(path):
            tencent_refs.append(path)
    allowed = {
        ROOT / "android/app/src/main/java/com/local/unifiedinbox/model/Messages.kt",
        ROOT / "android/app/src/main/java/com/local/unifiedinbox/model/Rule.kt",
    }
    check(
        set(tencent_refs) <= allowed,
        f"WeChat/QQ package references outside the allowlisted source filter: {tencent_refs}",
    )

    for path in walk("android", "app", "src", "main", suffixes={".kt"}):
        content = text(path)
        for forbidden in ("sendTextMessage(", "getExternalFilesDir(", "/data/data/com.tencent"):
            check(forbidden not in content, f"forbidden client-data/automation call in {path}: {forbidden}")

    for path in walk("backend", "app", suffixes={".py"}):
        content = text(path)
        for forbidden in ("subprocess", "os.system", "eval(", "exec(", "pickle.loads"):
            check(forbidden not in content, f"unexpected backend capability in {path}: {forbidden}")
        check("print(token" not in content, f"token may be printed in {path}")
        check('logger.info("token' not in content, f"token may be logged in {path}")

    for path in walk("web", "src", suffixes={".js", ".vue", ".css"}):
        content = text(path)
        for forbidden in ("v-html", "innerHTML", "document.write", "new Function(", "eval("):
            check(forbidden not in content, f"unsafe web sink in {path}: {forbidden}")
        check("http://" not in content, f"hardcoded insecure URL in {path}")

    for path in walk("web", suffixes={".js", ".mjs", ".json", ".html"}):
        content = text(path)
        check(
            not re.search(r"[?&](token|api_token)=", content),
            f"token appears in a URL query in {path}",
        )

    gitignore = ROOT / ".gitignore"
    check(gitignore.exists(), ".gitignore is missing")
    if gitignore.exists():
        ignored = text(gitignore)
        for expected in (".env", "backend/data/", "*.jks", "*.keystore"):
            check(expected in ignored, f".gitignore does not cover {expected}")

    if (ROOT / ".git").exists():
        tracked = subprocess.run(
            ["git", "-C", str(ROOT), "ls-files"],
            capture_output=True,
            text=True,
            check=False,
        ).stdout.splitlines()
        check(".env" not in tracked, ".env is tracked by git")
        check("backend/data/token.txt" not in tracked, "token file is tracked by git")

    if failures:
        print("SECURITY CHECK FAILED")
        for failure in failures:
            print(f" - {failure}")
        return 1
    print(f"SECURITY CHECK PASSED ({checks} checks)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
