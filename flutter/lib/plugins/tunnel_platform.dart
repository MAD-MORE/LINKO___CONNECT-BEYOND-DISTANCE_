import 'package:flutter/services.dart';

class TunnelPlatformStatus { const TunnelPlatformStatus({required this.available, required this.message}); final bool available; final String message; }
/// Bridge for Android VpnService and iOS NetworkExtension; no tunnel logic in Dart.
class TunnelPlatform {
  TunnelPlatform._();
  static const _channel = MethodChannel('com.linko.linkshare/tunnel');
  static Future<TunnelPlatformStatus> getPlatformStatus() async {
    try { final result = await _channel.invokeMapMethod<String, dynamic>('getPlatformStatus'); return TunnelPlatformStatus(available: result?['available'] as bool? ?? false, message: result?['message'] as String? ?? 'Native tunnel unavailable.'); }
    on MissingPluginException { return const TunnelPlatformStatus(available: false, message: 'Native tunnel plugin is not available on this platform yet.'); }
  }
  static Future<bool> startHostTunnel() => _invokeBoolean('startHostTunnel');
  static Future<bool> connectToHost() => _invokeBoolean('connectToHost');
  static Future<bool> disconnectTunnel() => _invokeBoolean('disconnectTunnel');
  static Future<bool> _invokeBoolean(String method) async { try { return await _channel.invokeMethod<bool>(method) ?? false; } on MissingPluginException { return false; } }
}
