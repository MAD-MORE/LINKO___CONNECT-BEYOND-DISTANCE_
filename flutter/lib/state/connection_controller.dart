import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'connection_state.dart';

final connectionControllerProvider = NotifierProvider<ConnectionController, LinkConnectionState>(ConnectionController.new);
class ConnectionController extends Notifier<LinkConnectionState> {
  @override LinkConnectionState build() => const LinkConnectionState();
  void selectMode(LinkMode mode) => state = state.copyWith(mode: mode, phase: ConnectionPhase.idle);
  void updatePhase(ConnectionPhase phase, {String? message}) => state = state.copyWith(phase: phase, message: message);
}
