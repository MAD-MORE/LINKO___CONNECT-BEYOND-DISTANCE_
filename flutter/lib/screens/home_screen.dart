import 'dart:math' as math;
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import '../state/connection_controller.dart';
import '../state/connection_state.dart';
import '../theme/linko_theme.dart';

class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final connection = ref.watch(connectionControllerProvider);
    final controller = ref.read(connectionControllerProvider.notifier);
    final isHost = connection.mode == LinkMode.host;
    final busy = connection.phase == ConnectionPhase.requesting ||
        connection.phase == ConnectionPhase.handshaking;
    final connected = connection.phase == ConnectionPhase.connected;
    final failed = connection.phase == ConnectionPhase.failed;

    return Scaffold(
      backgroundColor: LinkoColors.background,
      body: SafeArea(
        child: Stack(
          children: [
            const Positioned.fill(
              child: IgnorePointer(child: CustomPaint(painter: _AmbientPainter())),
            ),
            Center(
              child: ConstrainedBox(
                constraints: const BoxConstraints(maxWidth: 520),
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(22, 18, 22, 18),
                  child: Column(
                    children: [
                      _TopBar(connected: connected),
                      const Spacer(),
                      _Ring(
                        phase: connection.phase,
                        onTap: busy
                            ? null
                            : () => controller.updatePhase(
                                  isHost
                                      ? (connected
                                          ? ConnectionPhase.idle
                                          : ConnectionPhase.handshaking)
                                      : ConnectionPhase.requesting,
                                  message: isHost ? 'Ready to share' : 'Looking for a host',
                                ),
                      ),
                      const SizedBox(height: 24),
                      AnimatedSwitcher(
                        duration: const Duration(milliseconds: 220),
                        child: Text(
                          connected
                              ? 'CONNECTED'
                              : busy
                                  ? (isHost ? 'READY TO CONNECT' : 'CONNECTING')
                                  : failed
                                      ? 'CONNECTION FAILED'
                                      : isHost
                                          ? 'READY TO SHARE'
                                          : 'READY TO CONNECT',
                          key: ValueKey('${connection.phase}-${connection.mode}'),
                          style: TextStyle(
                            color: connected
                                ? LinkoColors.teal
                                : failed
                                    ? LinkoColors.danger
                                    : LinkoColors.textPrimary,
                            fontSize: 13,
                            fontWeight: FontWeight.w800,
                            letterSpacing: 2.8,
                          ),
                        ),
                      ),
                      const SizedBox(height: 9),
                      Text(
                        connection.message ??
                            (isHost
                                ? 'Share your connection securely.'
                                : 'Connect to a trusted LINKO device.'),
                        textAlign: TextAlign.center,
                        style: const TextStyle(
                          color: LinkoColors.textMuted,
                          fontSize: 14,
                        ),
                      ),
                      const SizedBox(height: 28),
                      _RoleSwitch(
                        mode: connection.mode,
                        onChanged: controller.selectMode,
                      ),
                      const SizedBox(height: 14),
                      _PrimaryButton(
                        busy: busy,
                        connected: connected,
                        isHost: isHost,
                        onPressed: () {
                          if (connected) {
                            controller.updatePhase(ConnectionPhase.idle, message: 'Connection ended');
                          } else if (isHost) {
                            controller.updatePhase(ConnectionPhase.handshaking, message: 'Waiting for receiver');
                          } else {
                            controller.updatePhase(ConnectionPhase.requesting, message: 'Searching for a host');
                          }
                        },
                      ),
                      const SizedBox(height: 16),
                      _StatusLine(phase: connection.phase),
                      const Spacer(),
                      const Text(
                        'LINKO  •  CONNECT BEYOND DISTANCE',
                        style: TextStyle(
                          color: LinkoColors.textMuted,
                          fontFamily: 'monospace',
                          fontSize: 9,
                          letterSpacing: 1.4,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _TopBar extends StatelessWidget {
  const _TopBar({required this.connected});
  final bool connected;

  @override
  Widget build(BuildContext context) {
    return Row(
      children: [
        Container(
          width: 38,
          height: 38,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            border: Border.all(color: LinkoColors.teal.withValues(alpha: .45)),
            color: LinkoColors.surface.withValues(alpha: .72),
          ),
          child: const Icon(Icons.link_rounded, color: LinkoColors.teal, size: 19),
        ),
        const SizedBox(width: 11),
        const Text(
          'LINKO',
          style: TextStyle(
            color: LinkoColors.textPrimary,
            fontSize: 18,
            fontWeight: FontWeight.w800,
            letterSpacing: 4,
          ),
        ),
        const Spacer(),
        _OnlineDot(connected: connected),
      ],
    );
  }
}

class _OnlineDot extends StatelessWidget {
  const _OnlineDot({required this.connected});
  final bool connected;

  @override
  Widget build(BuildContext context) {
    final color = connected ? LinkoColors.teal : LinkoColors.textMuted;
    return Row(
      children: [
        Container(
          width: 7,
          height: 7,
          decoration: BoxDecoration(
            color: color,
            shape: BoxShape.circle,
            boxShadow: [BoxShadow(color: color.withValues(alpha: .55), blurRadius: 8)],
          ),
        ),
        const SizedBox(width: 7),
        Text(
          connected ? 'LINK ACTIVE' : 'LINKO READY',
          style: TextStyle(
            color: color,
            fontFamily: 'monospace',
            fontSize: 9,
            fontWeight: FontWeight.w700,
            letterSpacing: 1.1,
          ),
        ),
      ],
    );
  }
}

class _RoleSwitch extends StatelessWidget {
  const _RoleSwitch({required this.mode, required this.onChanged});
  final LinkMode mode;
  final ValueChanged<LinkMode> onChanged;

  @override
  Widget build(BuildContext context) {
    return Container(
      height: 48,
      padding: const EdgeInsets.all(4),
      decoration: BoxDecoration(
        color: LinkoColors.surface.withValues(alpha: .78),
        borderRadius: BorderRadius.circular(18),
        border: Border.all(color: LinkoColors.border),
      ),
      child: Row(
        children: [
          _RoleButton(
            selected: mode == LinkMode.client,
            label: 'CONNECT',
            icon: Icons.radar_rounded,
            onTap: () => onChanged(LinkMode.client),
          ),
          _RoleButton(
            selected: mode == LinkMode.host,
            label: 'SHARE',
            icon: Icons.wifi_tethering_rounded,
            onTap: () => onChanged(LinkMode.host),
          ),
        ],
      ),
    );
  }
}

class _RoleButton extends StatelessWidget {
  const _RoleButton({required this.selected, required this.label, required this.icon, required this.onTap});
  final bool selected;
  final String label;
  final IconData icon;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Material(
        color: selected ? LinkoColors.blue.withValues(alpha: .16) : Colors.transparent,
        borderRadius: BorderRadius.circular(14),
        child: InkWell(
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Icon(icon, size: 16, color: selected ? LinkoColors.blue : LinkoColors.textMuted),
              const SizedBox(width: 7),
              Text(
                label,
                style: TextStyle(
                  color: selected ? LinkoColors.textPrimary : LinkoColors.textMuted,
                  fontSize: 11,
                  fontWeight: FontWeight.w800,
                  letterSpacing: 1.3,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _PrimaryButton extends StatelessWidget {
  const _PrimaryButton({required this.busy, required this.connected, required this.isHost, required this.onPressed});
  final bool busy;
  final bool connected;
  final bool isHost;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    return SizedBox(
      width: double.infinity,
      height: 58,
      child: FilledButton.icon(
        onPressed: busy ? null : onPressed,
        icon: busy
            ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
            : Icon(connected ? Icons.stop_rounded : (isHost ? Icons.wifi_tethering_rounded : Icons.radar_rounded)),
        label: Text(
          busy
              ? 'CONNECTING…'
              : connected
                  ? 'DISCONNECT'
                  : isHost
                      ? 'SHARE CONNECTION'
                      : 'CONNECT',
          style: const TextStyle(fontWeight: FontWeight.w800, letterSpacing: 1.2),
        ),
        style: FilledButton.styleFrom(
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18)),
          backgroundColor: connected ? LinkoColors.danger : LinkoColors.blue,
          foregroundColor: Colors.white,
        ),
      ),
    );
  }
}

class _StatusLine extends StatelessWidget {
  const _StatusLine({required this.phase});
  final ConnectionPhase phase;

  @override
  Widget build(BuildContext context) {
    final color = switch (phase) {
      ConnectionPhase.connected => LinkoColors.teal,
      ConnectionPhase.failed => LinkoColors.danger,
      ConnectionPhase.requesting || ConnectionPhase.handshaking => LinkoColors.blue,
      ConnectionPhase.idle => LinkoColors.textMuted,
    };
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Icon(Icons.shield_outlined, size: 14, color: color),
        const SizedBox(width: 6),
        Text(
          phase == ConnectionPhase.connected ? 'ENCRYPTED LINK ACTIVE' : 'SECURE LINK STANDBY',
          style: TextStyle(color: color, fontFamily: 'monospace', fontSize: 9, letterSpacing: 1.1),
        ),
      ],
    );
  }
}

class _Ring extends StatelessWidget {
  const _Ring({required this.phase, required this.onTap});
  final ConnectionPhase phase;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) {
    final active = phase != ConnectionPhase.idle;
    return GestureDetector(
      onTap: onTap,
      child: SizedBox(
        width: 270,
        height: 270,
        child: TweenAnimationBuilder<double>(
          tween: Tween(begin: 0, end: active ? 1 : 0),
          duration: const Duration(milliseconds: 700),
          curve: Curves.easeOut,
          builder: (context, value, child) => CustomPaint(
            painter: _RingPainter(phase: phase, energy: value),
            child: child,
          ),
          child: Center(
            child: Container(
              width: 106,
              height: 106,
              decoration: BoxDecoration(
                shape: BoxShape.circle,
                color: LinkoColors.surface,
                border: Border.all(color: LinkoColors.teal.withValues(alpha: .45), width: 1.2),
                boxShadow: [BoxShadow(color: LinkoColors.teal.withValues(alpha: .12), blurRadius: 30, spreadRadius: 4)],
              ),
              child: Icon(
                phase == ConnectionPhase.connected ? Icons.link_rounded : Icons.hub_rounded,
                color: phase == ConnectionPhase.failed ? LinkoColors.danger : LinkoColors.teal,
                size: 38,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _RingPainter extends CustomPainter {
  const _RingPainter({required this.phase, required this.energy});
  final ConnectionPhase phase;
  final double energy;

  @override
  void paint(Canvas canvas, Size size) {
    final center = size.center(Offset.zero);
    final r = size.shortestSide * .42;
    final color = phase == ConnectionPhase.failed
        ? LinkoColors.danger
        : phase == ConnectionPhase.connected
            ? LinkoColors.teal
            : LinkoColors.blue;

    for (var i = 0; i < 4; i++) {
      final rr = r - i * 20.0;
      final opacity = i == 0 ? .48 : .18;
      final paint = Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = i == 0 ? 2.2 : 1
        ..color = color.withValues(alpha: opacity + energy * .08);
      canvas.drawCircle(center, rr, paint);
    }

    final sweep = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..color = color.withValues(alpha: .82);
    final rect = Rect.fromCircle(center: center, radius: r);
    final start = -math.pi / 2 + (phase.index * .55);
    canvas.drawArc(rect, start, math.pi * .55 + energy * math.pi * .55, false, sweep);

    for (var i = 0; i < 8; i++) {
      final a = (i / 8) * math.pi * 2 + start;
      final p = Offset(center.dx + math.cos(a) * r, center.dy + math.sin(a) * r);
      canvas.drawCircle(p, i % 2 == 0 ? 2.4 : 1.4, Paint()..color = color.withValues(alpha: .7));
    }
  }

  @override
  bool shouldRepaint(covariant _RingPainter oldDelegate) => oldDelegate.phase != phase || oldDelegate.energy != energy;
}

class _AmbientPainter extends CustomPainter {
  const _AmbientPainter();

  @override
  void paint(Canvas canvas, Size size) {
    final points = [
      Offset(size.width * .08, size.height * .18),
      Offset(size.width * .9, size.height * .23),
      Offset(size.width * .14, size.height * .76),
      Offset(size.width * .86, size.height * .82),
    ];
    final line = Paint()
      ..color = LinkoColors.blue.withValues(alpha: .055)
      ..strokeWidth = 1;
    final dot = Paint()..color = LinkoColors.teal.withValues(alpha: .16);
    for (var i = 0; i < points.length; i++) {
      canvas.drawCircle(points[i], 2, dot);
      for (var j = i + 1; j < points.length; j++) {
        if ((points[i] - points[j]).distance < size.width * .9) {
          canvas.drawLine(points[i], points[j], line);
        }
      }
    }
  }

  @override
  bool shouldRepaint(covariant _AmbientPainter oldDelegate) => false;
}
