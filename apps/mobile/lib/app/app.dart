import 'package:flutter/material.dart';
import '../features/home/presentation/home_screen.dart';
import 'theme/app_theme.dart';

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
