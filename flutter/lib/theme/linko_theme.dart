import 'package:flutter/material.dart';

abstract final class LinkoColors {
  static const background = Color(0xFF0A0E14);
  static const backgroundElevated = Color(0xFF101A24);
  static const surface = Color(0xFF13212C);
  static const teal = Color(0xFF4AE0A0);
  static const blue = Color(0xFF4A8FE0);
  static const textPrimary = Color(0xFFF2F7FA);
  static const textMuted = Color(0xFF7D92A3);
  static const border = Color(0xFF29404F);
  static const danger = Color(0xFFFF6B7A);
}

abstract final class LinkoTheme {
  static ThemeData get dark => ThemeData(
    useMaterial3: true, brightness: Brightness.dark, scaffoldBackgroundColor: LinkoColors.background,
    colorScheme: const ColorScheme.dark(primary: LinkoColors.teal, secondary: LinkoColors.blue, surface: LinkoColors.surface, error: LinkoColors.danger, onPrimary: LinkoColors.background, onSecondary: LinkoColors.textPrimary, onSurface: LinkoColors.textPrimary),
    textTheme: const TextTheme(bodyLarge: TextStyle(color: LinkoColors.textPrimary, height: 1.5), bodyMedium: TextStyle(color: LinkoColors.textMuted, height: 1.45), labelLarge: TextStyle(color: LinkoColors.textMuted, letterSpacing: 1.2)),
    cardTheme: CardThemeData(color: LinkoColors.surface, elevation: 0, margin: EdgeInsets.zero, shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(18), side: const BorderSide(color: LinkoColors.border))),
    filledButtonTheme: FilledButtonThemeData(style: FilledButton.styleFrom(backgroundColor: LinkoColors.teal, foregroundColor: LinkoColors.background, minimumSize: const Size.fromHeight(52), textStyle: const TextStyle(fontWeight: FontWeight.w800, letterSpacing: .3), shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)))),
  );
}
