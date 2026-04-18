import 'package:flutter_test/flutter_test.dart';
import 'package:route_mates_mobile/core/config/app_config.dart';

void main() {
  test('uses configured API_BASE_URL when provided', () {
    final apiBaseUrl = AppConfig.resolveApiBaseUrl(
      apiBaseUrlOverride: 'https://api.example.com',
      isReleaseMode: true,
      isIOS: false,
    );

    expect(apiBaseUrl, 'https://api.example.com');
  });

  test('uses Android emulator default in debug mode when URL is not provided', () {
    final apiBaseUrl = AppConfig.resolveApiBaseUrl(
      apiBaseUrlOverride: '',
      isReleaseMode: false,
      isIOS: false,
    );

    expect(apiBaseUrl, 'http://10.0.2.2:3000');
  });

  test('uses iOS simulator default in debug mode when URL is not provided', () {
    final apiBaseUrl = AppConfig.resolveApiBaseUrl(
      apiBaseUrlOverride: '',
      isReleaseMode: false,
      isAndroid: false,
      isIOS: true,
    );

    expect(apiBaseUrl, 'http://localhost:3000');
  });

  test('throws in release mode when API_BASE_URL is missing', () {
    expect(
      () => AppConfig.resolveApiBaseUrl(
        apiBaseUrlOverride: '',
        isReleaseMode: true,
        isIOS: false,
      ),
      throwsStateError,
    );
  });

  test('throws in release mode when API_BASE_URL uses local-only host', () {
    expect(
      () => AppConfig.resolveApiBaseUrl(
        apiBaseUrlOverride: 'http://10.0.2.2:3000',
        isReleaseMode: true,
        isIOS: false,
      ),
      throwsStateError,
    );
  });
}
