import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../state/connection_controller.dart';
import '../state/connection_state.dart';
import '../theme/linko_theme.dart';
import '../widgets/mode_selector.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});
  @override Widget build(BuildContext context, WidgetRef ref) {
    final connection = ref.watch(connectionControllerProvider); final isHost = connection.mode == LinkMode.host;
    return Scaffold(body: Stack(children: [
      const Positioned.fill(child: IgnorePointer(child: CustomPaint(painter: _NetworkPainter()))),
      SafeArea(child: Center(child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 520), child: SingleChildScrollView(padding: const EdgeInsets.fromLTRB(24, 34, 24, 28), child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
        const _Brand(), const SizedBox(height: 44), const Text('CONNECTION ROLE', style: TextStyle(color: LinkoColors.textMuted, fontFamily: 'monospace', letterSpacing: 2, fontSize: 11)), const SizedBox(height: 10),
        ModeSelector(mode: connection.mode, onChanged: ref.read(connectionControllerProvider.notifier).selectMode), const SizedBox(height: 24), _ActionCard(isHost: isHost), const SizedBox(height: 20), _Status(phase: connection.phase),
      ]))))
    ]));
  }
}
class _Brand extends StatelessWidget { const _Brand(); @override Widget build(BuildContext context) => Column(children: [Container(width: 46, height: 46, decoration: BoxDecoration(shape: BoxShape.circle, border: Border.all(color: LinkoColors.teal.withValues(alpha: .7)), boxShadow: [BoxShadow(color: LinkoColors.teal.withValues(alpha: .22), blurRadius: 22)]), child: const Icon(Icons.hub_rounded, color: LinkoColors.teal)), const SizedBox(height: 19), const Text('LINKO', style: TextStyle(color: LinkoColors.textPrimary, fontSize: 38, fontWeight: FontWeight.w700, letterSpacing: 8, shadows: [Shadow(color: LinkoColors.teal, blurRadius: 15)])), const SizedBox(height: 10), const Text('BEYOND DISTANCE PROTOCOLS', style: TextStyle(color: LinkoColors.textMuted, fontFamily: 'monospace', fontSize: 10, letterSpacing: 1.8))]); }
class _ActionCard extends StatelessWidget { const _ActionCard({required this.isHost}); final bool isHost; @override Widget build(BuildContext context) => Card(child: Padding(padding: const EdgeInsets.all(22), child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [Icon(isHost ? Icons.cast_connected_rounded : Icons.vpn_key_rounded, color: LinkoColors.blue, size: 27), const SizedBox(height: 18), Text(isHost ? 'Share this device\'s internet' : 'Connect to a host device', style: const TextStyle(color: LinkoColors.textPrimary, fontSize: 21, fontWeight: FontWeight.w700)), const SizedBox(height: 8), Text(isHost ? 'Approve a request to open an encrypted connection.' : 'Choose a trusted host and request a private connection.', style: Theme.of(context).textTheme.bodyMedium), const SizedBox(height: 22), FilledButton.icon(onPressed: () {}, icon: Icon(isHost ? Icons.power_settings_new_rounded : Icons.radar_rounded), label: Text(isHost ? 'Start hosting' : 'Find a host'))]))); }
class _Status extends StatelessWidget { const _Status({required this.phase}); final ConnectionPhase phase; @override Widget build(BuildContext context) { final color = switch (phase) { ConnectionPhase.connected => LinkoColors.teal, ConnectionPhase.failed => LinkoColors.danger, ConnectionPhase.requesting || ConnectionPhase.handshaking => LinkoColors.blue, ConnectionPhase.idle => LinkoColors.textMuted }; return Row(mainAxisAlignment: MainAxisAlignment.center, children: [Container(width: 8, height: 8, decoration: BoxDecoration(color: color, shape: BoxShape.circle, boxShadow: [BoxShadow(color: color.withValues(alpha: .7), blurRadius: 9)])), const SizedBox(width: 9), Text('TUNNEL / ${phase.name.toUpperCase()}', style: TextStyle(color: color, fontFamily: 'monospace', fontWeight: FontWeight.w700, fontSize: 11, letterSpacing: 1.3))]); }}
class _NetworkPainter extends CustomPainter { const _NetworkPainter(); @override void paint(Canvas canvas, Size size) { final points = [Offset(size.width * .10, 90), Offset(size.width * .83, 160), Offset(size.width * .08, size.height * .72), Offset(size.width * .88, size.height * .82), Offset(size.width * .68, size.height * .40)]; final line = Paint()..color = LinkoColors.blue.withValues(alpha: .11)..strokeWidth = 1; final dot = Paint()..color = LinkoColors.teal.withValues(alpha: .34); for (var i = 0; i < points.length; i++) { canvas.drawCircle(points[i], 2.5, dot); for (var j = i + 1; j < points.length; j++) { if ((points[i] - points[j]).distance < math.max(size.width, size.height) * .58) canvas.drawLine(points[i], points[j], line); } }} @override bool shouldRepaint(covariant _NetworkPainter oldDelegate) => false; }
