#!/usr/bin/env python3
"""LINKO cross-layer integrity gate (Supabase Control Plane)."""
from __future__ import annotations
from pathlib import Path
import re

ROOT = Path(__file__).resolve().parents[1]
REPO = ROOT.parent
ANDROID_SRC = ROOT / "app" / "src" / "main"
KOTLIN = ANDROID_SRC / "java" / "com" / "linkshare" / "app"
REQUIRED_FILES = [
    ROOT / "settings.gradle.kts", ROOT / "build.gradle.kts", ROOT / "app" / "build.gradle.kts",
    ROOT / "app" / "src" / "main" / "AndroidManifest.xml",
    KOTLIN / "MainActivity.kt", KOTLIN / "Navigation.kt",
    KOTLIN / "ui" / "screens" / "LinkoApp.kt", KOTLIN / "ui" / "screens" / "SignUpScreen.kt",
    KOTLIN / "ui" / "screens" / "AuthScreens.kt", KOTLIN / "ui" / "screens" / "FriendsScreens.kt",
    KOTLIN / "viewmodel" / "LinkShareViewModel.kt", KOTLIN / "network" / "LinkoRuntime.kt",
    KOTLIN / "network" / "LinkoRuntimeConfig.kt", KOTLIN / "network" / "LinkoEngineBridge.kt",
    KOTLIN / "network" / "LinkoControlPlaneApi.kt", KOTLIN / "network" / "LinkoFriendsApi.kt",
    KOTLIN / "network" / "LinkoDeviceRegistrar.kt", KOTLIN / "network" / "LinkoDeviceControlApi.kt",
    KOTLIN / "network" / "LinkoRealtimeManager.kt",
    KOTLIN / "vpn" / "LinkShareVpnService.kt", KOTLIN / "provider" / "LinkoProviderService.kt",
]
FORBIDDEN_PRODUCTION_REFERENCES = ("MockLinkShareRepository", "mockFriends", "fakeFriends", "FakeLinkoFriendsApi")
FRIEND_EDGE_PATH = "/functions/v1/linko-friends"
FRIEND_PATHS = ("/profile", "/search?q=", "/requests", "/requests/respond", "/friends")


def read(path: Path) -> str:
    return path.read_text(encoding="utf-8")


def fail(errors: list[str]) -> int:
    if errors:
        print("LINKO integrity gate FAILED")
        for error in errors:
            print(f"- {error}")
        return 1
    print("LINKO integrity gate PASSED (Supabase Control Plane)")
    return 0


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
    if "BuildConfig.LINKO_SUPABASE_URL" not in friends_api:
        errors.append("friend API does not use the configured Supabase URL")
    if FRIEND_EDGE_PATH not in friends_api:
        errors.append("friend API is not wired to the configured Supabase Edge Function path")
    if re.search(r"https://[A-Za-z0-9.-]+\.supabase\.co/functions/v1/linko-friends", friends_api):
        errors.append("friend API hard-codes a Supabase project URL")
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
    device_api = read(KOTLIN / "network" / "LinkoDeviceControlApi.kt")
    device_registrar = read(KOTLIN / "network" / "LinkoDeviceRegistrar.kt")

    supabase_rpcs = {
        "linko_register_device": device_registrar,
        "linko_mark_presence": control_api,
        "linko_create_session": control_api,
        "linko_transition_session": control_api,
        "linko_pending_provider_requests": control_api,
        "linko_tunnel_config": control_api,
    }
    for rpc, source in supabase_rpcs.items():
        if rpc not in source and rpc not in device_api:
            errors.append(f"control-plane contract missing Supabase RPC token: {rpc}")

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

    migrations = read(REPO / "backend" / "migrations" / "ALL_MIGRATIONS_COMBINED.sql")
    for rpc in ("linko_register_device", "linko_mark_presence", "linko_create_session", "linko_transition_session", "linko_tunnel_config"):
        if rpc not in migrations:
            errors.append(f"Supabase migration suite missing RPC function: {rpc}")

    gradle = read(ROOT / "app" / "build.gradle.kts")
    if "buildConfig = true" not in gradle:
        errors.append("Android buildConfig feature is disabled")
    if "LINKO_SUPABASE_URL" not in gradle:
        errors.append("LINKO_SUPABASE_URL is not wired into BuildConfig")

    return fail(errors)


if __name__ == "__main__":
    raise SystemExit(main())
