/// Contract placeholder. Add endpoint, auth, and WebSocket schemas when available.
class SignalingApiClient {
  const SignalingApiClient({required this.baseUrl});
  final Uri baseUrl;
  Future<void> requestConnection({required String hostId}) => throw UnimplementedError('Awaiting Linko signaling API contract.');
  Stream<Object> connectionEvents() => throw UnimplementedError('Awaiting Linko WebSocket event schema.');
}
