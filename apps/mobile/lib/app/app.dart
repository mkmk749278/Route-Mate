import 'package:flutter/material.dart';
import 'package:route_mates_mobile/app/theme/app_theme.dart';
import 'package:route_mates_mobile/features/auth/presentation/auth_screen.dart';
import 'package:route_mates_mobile/features/dashboard/presentation/dashboard_screen.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

class RouteMatesApp extends StatelessWidget {
  const RouteMatesApp({
    required this.controller,
    super.key,
  });

  final AppController controller;

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Route Mates',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      home: AnimatedBuilder(
        animation: controller,
        builder: (context, _) {
          if (controller.isRestoringSession) {
            return const Scaffold(
              body: Center(child: CircularProgressIndicator()),
            );
          }

          if (!controller.isAuthenticated) {
            return AuthScreen(controller: controller);
          }

          return DashboardScreen(controller: controller);
        },
      ),
    );
  }
}
