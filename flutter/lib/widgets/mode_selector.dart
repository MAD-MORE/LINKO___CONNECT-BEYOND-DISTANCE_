import 'package:flutter/material.dart';
import '../state/connection_state.dart';
import '../theme/linko_theme.dart';

class ModeSelector extends StatelessWidget {
  const ModeSelector({super.key, required this.mode, required this.onChanged});
  final LinkMode mode; final ValueChanged<LinkMode> onChanged;
  @override Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(color: LinkoColors.backgroundElevated, border: Border.all(color: LinkoColors.border), borderRadius: BorderRadius.circular(14)),
    child: Row(children: [
      _Option(icon: Icons.wifi_tethering_rounded, label: 'HOST', selected: mode == LinkMode.host, onTap: () => onChanged(LinkMode.host)),
      _Option(icon: Icons.phonelink_lock_rounded, label: 'CLIENT', selected: mode == LinkMode.client, onTap: () => onChanged(LinkMode.client)),
    ]),
  );
}
class _Option extends StatelessWidget {
  const _Option({required this.icon, required this.label, required this.selected, required this.onTap});
  final IconData icon; final String label; final bool selected; final VoidCallback onTap;
  @override Widget build(BuildContext context) => Expanded(child: InkWell(onTap: onTap, borderRadius: BorderRadius.circular(12), child: AnimatedContainer(duration: const Duration(milliseconds: 180), margin: const EdgeInsets.all(4), padding: const EdgeInsets.symmetric(vertical: 15), decoration: BoxDecoration(color: selected ? LinkoColors.teal.withValues(alpha: .14) : Colors.transparent, border: Border.all(color: selected ? LinkoColors.teal : Colors.transparent), borderRadius: BorderRadius.circular(10), boxShadow: selected ? [BoxShadow(color: LinkoColors.teal.withValues(alpha: .16), blurRadius: 14)] : null), child: Row(mainAxisAlignment: MainAxisAlignment.center, children: [Icon(icon, size: 18, color: selected ? LinkoColors.teal : LinkoColors.textMuted), const SizedBox(width: 8), Text(label, style: TextStyle(color: selected ? LinkoColors.teal : LinkoColors.textMuted, fontWeight: FontWeight.w800, fontSize: 12, letterSpacing: 1.3))]))));
}
