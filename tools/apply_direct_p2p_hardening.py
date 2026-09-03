from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
path = ROOT / "android/app/src/main/java/com/linkshare/app/network/DirectP2pNegotiator.kt"
text = path.read_text()

old = '''                    val sequence = signal.payload.optLong("seq", 0L)\n                    if (sequence > 0 && sequence <= highestRemoteSequence) return\n                    if (sequence > highestRemoteSequence) highestRemoteSequence = sequence\n                    if (signal.payload.optBoolean("endOfCandidates", false)) return\n                    val host = signal.payload.optString("candidate").trim()\n'''
new = '''                    val sequence = signal.payload.optLong("seq", 0L)\n                    if (signal.payload.optBoolean("endOfCandidates", false)) return\n                    val host = signal.payload.optString("candidate").trim()\n'''
if old not in text:
    raise SystemExit("Remote candidate sequence anchor not found")
text = text.replace(old, new)

old = '''                    val id = signal.payload.optString("candidateId").ifBlank { "legacy-${address.hostAddress}:$port" }\n                    remoteCandidates[id] = IceCandidate(id, signal.payload.optString("foundation", id), type, address, port,\n                        signal.payload.optInt("priority", candidatePriority(type)), incomingGeneration, sequence)\n'''
new = '''                    val id = signal.payload.optString("candidateId").ifBlank { "legacy-${address.hostAddress}:$port" }\n                    // Realtime delivery is not guaranteed to preserve candidate order.\n                    // Candidate identity, not a global sequence watermark, is the dedupe key.\n                    if (remoteCandidates.containsKey(id)) return\n                    remoteCandidates[id] = IceCandidate(id, signal.payload.optString("foundation", id), type, address, port,\n                        signal.payload.optInt("priority", candidatePriority(type)), incomingGeneration, sequence)\n'''
if old not in text:
    raise SystemExit("Remote candidate identity anchor not found")
text = text.replace(old, new)

text = text.replace('import java.util.concurrent.atomic.AtomicBoolean\n', 'import java.util.concurrent.atomic.AtomicBoolean\n')
path.write_text(text)
