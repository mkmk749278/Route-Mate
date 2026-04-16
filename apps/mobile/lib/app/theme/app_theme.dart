import 'package:flutter/material.dart';

class AppTheme {
  static ThemeData light() {
    const seed = Color(0xFF1E88E5);
    return ThemeData(
      colorScheme: ColorScheme.fromSeed(seedColor: seed),
      useMaterial3: true,
    );
  }
}
