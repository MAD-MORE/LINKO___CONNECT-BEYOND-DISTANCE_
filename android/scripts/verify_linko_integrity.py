#!/usr/bin/env python3
"""LINKO cross-layer integrity gate."""
from __future__ import annotations
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
ANDROID_SRC = ROOT / "app" / "src" / "main"
KOTLIN = ANDROID_SRC / "java" / "com" / "linkshare" / "app"
REQUIRED_FILES = [
    ROOT / "settings.gradle.kts",
    ROOT / "build.gradle.kts",
    ROOT / "app" / "build.gradle.kts",
    ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
    KOTLIN / "MainActivity.kt",
    KOTLIN / "Navigation.kt",
    KOTLIN / "ui" / "LinkShareApp.kt",
    KOTLIN / "ui" / "screens" / "LinkoApp.kt",
    KOTLIN / "ui" / "screens" / "SignUpScreen.kt",
    KOTLIN / "ui" / "screens" / "AuthScreens.kt",
    KOTLIN / "ui" / "screens" / "FriendsScreens.kt",
    KOTLIN / "viewmodel" / "LinkShareViewModel.kt",
    KOTLIN / "network" / "LinkoRuntime.kt",
    KOTLIN / "network" / "LinkoRuntimeConfig.kt",
    KOTLIN / "network" / "LinkoEngineBridge.kt",
    KOTLIN / "network" / "LinkoControlPlaneApi.kt",
    KOTLIN / "network" / "LinkoFriendsApi.kt",
    KOTLIN / "network" / "LinkoDeviceRegistrar.kt",
    KOTLIN / "network" / "LinkoSignalingClient.kt",
    KOTLIN / "vpn" / "LinkShareVpnService.kt",
    KOTLIN / "provider" / "LinkoProviderService.kt",
]
FORBIDDEN_PRODUCTION_REFERENCES = ("MockLinkShareRepository", "mockFriends", "fakeFriends", "FakeLinkoFriendsApi")
FRIEND_EDGE_BASE = "https://pbnvssbtshvesqwhckfa.supabase.co/functions/v1/linko-friends"
FRIEND_PATHS = ("/profile", "/search?q=", "/requests", "/requests/respond", "/friends")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def normalize_backend_source(source: str) -> str:
    """Normalize JS/TS escaped URL slashes so route checks compare semantics, not syntax."""
    return source.replace("\\/", "/")


def fail(errors: list[str]) -> int:
    if errors:
        print("LINKO integrity gate FAILED")
        for error in errors:
            print(f"- {error}")
        return 1
    print("LINKO integrity gate PASSED")
    return 0


def contains_session_contract(source: str, semantic_path: str) -> bool:
    if "$sessionId" not in semantic_path:
        return semantic_path in source
    prefix, suffix = semantic_path.split("$sessionId", 1)
    return prefix in source and suffix in source


def backend_has_dynamic_route(source: str, variable_name: str, path_suffix: str) -> bool:
    """Check a real url.pathname.match() dynamic route independent of slash escaping."""
    pattern = (
        rf"{re.escape(variable_name)}\s*=\s*url\.pathname\.match\("
        rf"/\^/v1/sessions/\(\[\^/\]\+\)/{re.escape(path_suffix)}\$/"
    )
    return re.search(pattern, normalize_backend_source(source)) is not None


def main() -> int:
    errors: list[str] = []

    for path in REQUIRED_FILES:
        if not path.is_file():
            errors.append(f"missing required file: {path.relative_to(REPO)}")
    if errors:
        return fail(errors)

    kotlin_files = sorted(KOTLIN.rglob("*.kt"))
    for token in FORBIDDEN_PRODUCTION_REFERENCES:
        for path in kotlin_files:
            if path.name == "MockLinkShareRepository.kt":
                continue
            if token in read(path):
                errors.append(f"forbidden mock reference '{token}' in {path.relative_to(REPO)}")

    friends_api = read(KOTLIN / "network" / "LinkoFriendsApi.kt")
    if FRIEND_EDGE_BASE not in friends_api:
        errors.append("friend API is not wired to the configured Supabase Edge Function")
    for path in FRIEND_PATHS:
        if path not in friends_api:
            errors.append(f"friend workflow contract missing Android path: {path}")

    friends_ui = read(KOTLIN / "ui" / "screens" / "FriendsScreens.kt")
    if "relationshipStatus" not in friends_api:
        errors.append("FriendSearchResult.relationshipStatus missing")
    if "relationship_status" not in friends_api:
        errors.append("Android friend parser does not read relationship_status")
    if "relationshipStatus" not in friends_ui:
        errors.append("friend-search UI is not wired to relationshipStatus")

    control_api = read(KOTLIN / "network" / "LinkoControlPlaneApi.kt")
    device_registrar = read(KOTLIN / "network" / "LinkoDeviceRegistrar.kt")
    signaling_client = read(KOTLIN / "network" / "LinkoSignalingClient.kt")
    control_sources = {
        "/v1/devices/register": device_registrar,
        "/v1/devices/presence": control_api,
        "/v1/sessions": control_api,
        "/v1/sessions/$sessionId/signaling": signaling_client,
        "/v1/sessions/$sessionId/signaling/ticket": signaling_client,
        "/v1/sessions/$sessionId/tunnel": control_api,
    }
    for path, source in control_sources.items():
        if not contains_session_contract(source, path):
            errors.append(f"control-plane contract missing Android path/token: {path}")

    manifest = read(ANDROID_SRC / "AndroidManifest.xml")
    if "LinkShareVpnService" not in manifest:
        errors.append("VPN service is not declared in AndroidManifest.xml")
    if "android.permission.INTERNET" not in manifest:
        errors.append("INTERNET permission missing")

    engine_bridge = read(KOTLIN / "network" / "LinkoEngineBridge.kt")
    runtime = read(KOTLIN / "network" / "LinkoRuntime.kt")
    app = read(KOTLIN / "ui" / "screens" / "LinkoApp.kt")
    viewmodel = read(KOTLIN / "viewmodel" / "LinkShareViewModel.kt")
    if "LinkoEngineBridge" not in runtime:
        errors.append("LinkoRuntime does not reference LinkoEngineBridge")
    if "LinkoRuntime" not in app and "LinkoRuntime" not in viewmodel:
        errors.append("frontend does not reference LinkoRuntime")
    if not re.search(r"connect|request|session", engine_bridge, re.IGNORECASE):
        errors.append("LinkoEngineBridge contains no connection/session wiring")

    backend_server = REPO / "backend" / "src" / "server.ts"
    if backend_server.is_file():
        server = read(backend_server)
        normalized_server = normalize_backend_source(server)
        for token in ("/v1/sessions", "/v1/devices/register", "/v1/sessions/"):
            if token not in normalized_server:
                errors.append(f"backend control plane missing contract token: {token}")
        if not backend_has_dynamic_route(normalized_server, "signalTicketMatch", "signaling/ticket"):
            errors.append("backend control plane missing signaling ticket route")
        if not backend_has_dynamic_route(normalized_server, "signalMatch", "signaling"):
            errors.append("backend control plane missing signaling route")
        if not re.search(r'if \(req\.method === "POST" && signalTicketMatch\)', normalized_server):
            errors.append("backend signaling ticket handler is not POST-wired")
        if not re.search(r'if \(signalMatch && \(req\.method === "POST" \|\| req\.method === "GET"\)\)', normalized_server):
            errors.append("backend signaling handler is not GET/POST-wired")

    gradle = read(ROOT / "app" / "build.gradle.kts")
    if "buildConfig = true" not in gradle:
        errors.append("Android buildConfig feature is disabled")
    if "LINKO_CONTROL_PLANE_URL" not in gradle:
        errors.append("LINKO_CONTROL_PLANE_URL is not wired into BuildConfig")

    return fail(errors)


if __name__ == "__main__":
    raise SystemExit(main())
