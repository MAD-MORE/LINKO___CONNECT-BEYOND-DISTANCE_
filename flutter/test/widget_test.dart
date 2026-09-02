import 'package:flutter_test/flutter_test.dart';
import 'package:linko_flutter/main.dart';
void main() { testWidgets('shows Linko and switches modes', (tester) async { await tester.pumpWidget(const LinkoApp()); expect(find.text('LINKO'), findsOneWidget); expect(find.text('Share this device\'s internet'), findsOneWidget); await tester.tap(find.text('CLIENT')); await tester.pump(); expect(find.text('Connect to a host device'), findsOneWidget); }); }
