enum ConnectionPhase { idle, requesting, handshaking, connected, failed }
enum LinkMode { host, client }

class LinkConnectionState {
  const LinkConnectionState({this.mode = LinkMode.host, this.phase = ConnectionPhase.idle, this.message});
  final LinkMode mode;
  final ConnectionPhase phase;
  final String? message;
  LinkConnectionState copyWith({LinkMode? mode, ConnectionPhase? phase, String? message}) => LinkConnectionState(mode: mode ?? this.mode, phase: phase ?? this.phase, message: message);
}
