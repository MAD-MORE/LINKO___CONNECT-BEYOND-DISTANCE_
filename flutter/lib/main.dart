import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'screens/home_screen.dart';
import 'theme/linko_theme.dart';

void main() => runApp(const ProviderScope(child: LinkoApp()));

class LinkoApp extends StatelessWidget {
  const LinkoApp({super.key});
  @override
  Widget build(BuildContext context) => MaterialApp(
    title: 'Linko', debugShowCheckedModeBanner: false, theme: LinkoTheme.dark, home: const HomeScreen(),
  );
}
