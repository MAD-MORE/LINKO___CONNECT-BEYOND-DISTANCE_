#!/usr/bin/env python3
"""Black-box UDP forwarding smoke test for the deployed LINKO relay."""
from __future__ import annotations
import hashlib
import os
import random
import socket
import struct
import sys
import time
import uuid

MAGIC = b"LKO2"
HEADER_LENGTH = 95
RELAY_HOST = os.environ.get("LINKO_RELAY_HOST", "linko-relay.fly.dev")
RELAY_PORT = int(os.environ.get("LINKO_RELAY_UDP_PORT", "7000"))
TIMEOUT = float(os.environ.get("LINKO_UDP_SMOKE_TIMEOUT", "8"))


def build_packet(session_id: str, key_hash: bytes, role: int, packet_type: int, seq: int) -> bytes:
    if len(session_id) != 36 or len(key_hash) != 32:
        raise ValueError("invalid packet identity")
    header = bytearray(HEADER_LENGTH)
    header[0:4] = MAGIC
    header[4] = 0x02
    header[5:41] = session_id.encode("ascii")
    header[41:73] = key_hash
    header[73] = role
    header[74] = packet_type
    struct.pack_into(">Q", header, 75, seq)
    header[83:95] = os.urandom(12)
    return bytes(header) + b"LINKO-UDP-SMOKE-" + os.urandom(16)


def main() -> int:
    session_id = str(uuid.uuid4())
    key_hash = hashlib.sha256(os.urandom(32)).digest()
    provider_packet = build_packet(session_id, key_hash, 1, 4, random.randint(1, 1_000_000))
    client_packet = build_packet(session_id, key_hash, 2, 1, random.randint(1, 1_000_000))

    provider = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    client = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    provider.settimeout(TIMEOUT)
    client.settimeout(TIMEOUT)
    try:
        provider.bind(("0.0.0.0", 0))
        client.bind(("0.0.0.0", 0))
        relay = (RELAY_HOST, RELAY_PORT)
        provider.sendto(provider_packet, relay)
        time.sleep(0.25)
        client.sendto(client_packet, relay)
        received, address = provider.recvfrom(65535)
        if received != client_packet:
            print("UDP smoke test failed: forwarded datagram differs from source", file=sys.stderr)
            return 1
        print(f"UDP smoke test passed via {RELAY_HOST}:{RELAY_PORT}; relay peer={address[0]}:{address[1]}")
        return 0
    except OSError as exc:
        print(f"UDP smoke test failed: {exc}", file=sys.stderr)
        return 1
    finally:
        provider.close()
        client.close()


if __name__ == "__main__":
    raise SystemExit(main())
