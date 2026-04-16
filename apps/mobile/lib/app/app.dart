import 'package:flutter/material.dart';
import 'package:route_mates_mobile/app/theme/app_theme.dart';
import 'package:route_mates_mobile/features/home/presentation/home_screen.dart';

class RouteMatesApp extends StatelessWidget {
  const RouteMatesApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Route Mates',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.light(),
      home: const HomeScreen(),
    );
  }
}
