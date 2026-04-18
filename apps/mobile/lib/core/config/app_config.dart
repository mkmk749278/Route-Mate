import 'dart:io' show Platform;

import 'package:flutter/foundation.dart';

class AppConfig {
  static const String _apiBaseUrlDefine = String.fromEnvironment('API_BASE_URL');
  static const String _androidEmulatorApiBaseUrl = 'http://10.0.2.2:3000';
  static const String _iosSimulatorApiBaseUrl = 'http://localhost:3000';

  static String resolveApiBaseUrl({
    String? apiBaseUrlOverride,
    bool? isReleaseMode,
    bool? isIOS,
  }) {
    final configuredApiBaseUrl = (apiBaseUrlOverride ?? _apiBaseUrlDefine).trim();
    final releaseMode = isReleaseMode ?? kReleaseMode;

    if (configuredApiBaseUrl.isNotEmpty) {
      if (releaseMode && _isLocalOnlyHost(configuredApiBaseUrl)) {
        throw StateError(
          'Invalid API_BASE_URL for release/demo build: "$configuredApiBaseUrl". '
          'Use your deployed VPS/domain URL instead.',
        );
      }
      return configuredApiBaseUrl;
    }

    if (releaseMode) {
      throw StateError(
        'Missing API_BASE_URL for release/demo build. '
        'Set --dart-define=API_BASE_URL=https://your-vps-or-domain',
      );
    }

    final ios = isIOS ?? Platform.isIOS;

    if (ios) {
      return _iosSimulatorApiBaseUrl;
    }

    // Android emulator URL is used as the default for Android and other
    // non-iOS debug targets unless API_BASE_URL is explicitly provided.
    return _androidEmulatorApiBaseUrl;
  }

  static bool _isLocalOnlyHost(String value) {
    final host = Uri.tryParse(value)?.host.toLowerCase();
    if (host == null || host.isEmpty) {
      return false;
    }
    return host == '10.0.2.2' || host == 'localhost' || host == '127.0.0.1';
  }
}
