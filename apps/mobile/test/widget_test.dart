import 'package:flutter_test/flutter_test.dart';
import 'package:route_mates_mobile/app/app.dart';

void main() {
  testWidgets('shows Route Mates home shell', (WidgetTester tester) async {
    await tester.pumpWidget(const RouteMatesApp());

    expect(find.text('Route Mates'), findsWidgets);
    expect(find.text('MVP Bootstrap Stage'), findsOneWidget);
  });
}
