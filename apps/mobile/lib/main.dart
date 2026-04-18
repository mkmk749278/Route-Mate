import 'package:flutter/material.dart';
import 'package:route_mates_mobile/app/app.dart';
import 'package:route_mates_mobile/core/api/api_client.dart';
import 'package:route_mates_mobile/core/api/route_mates_api.dart';
import 'package:route_mates_mobile/core/config/app_config.dart';
import 'package:route_mates_mobile/core/storage/auth_token_storage.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final apiClient = ApiClient(baseUrl: AppConfig.resolveApiBaseUrl());
  final api = RouteMatesApi(apiClient);
  final tokenStorage = SharedPrefsAuthTokenStorage();
  final controller = AppController(api: api, tokenStorage: tokenStorage);

  await controller.initialize();

  runApp(RouteMatesApp(controller: controller));
}
