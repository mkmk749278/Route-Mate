import 'package:flutter_test/flutter_test.dart';
import 'package:route_mates_mobile/core/api/route_mates_api.dart';
import 'package:route_mates_mobile/core/storage/auth_token_storage.dart';
import 'package:route_mates_mobile/features/shared/models/models.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

void main() {
  test('restores stored token and loads current user', () async {
    final api = _ControllerFakeApi();
    final tokenStorage = _MemoryTokenStorage(initialToken: 'stored-token');
    final controller = AppController(api: api, tokenStorage: tokenStorage);

    await controller.initialize();

    expect(controller.isAuthenticated, isTrue);
    expect(controller.currentUser?.email, 'restore@example.com');
    expect(controller.profile?.id, 'user-1');
  });

  test('login persists access token', () async {
    final api = _ControllerFakeApi();
    final tokenStorage = _MemoryTokenStorage();
    final controller = AppController(api: api, tokenStorage: tokenStorage);

    await controller.initialize();
    await controller.login(email: 'login@example.com', password: 'password123');

    expect(tokenStorage.token, 'token-login');
    expect(controller.isAuthenticated, isTrue);
  });
}

class _MemoryTokenStorage implements AuthTokenStorage {
  _MemoryTokenStorage({String? initialToken}) : token = initialToken;

  String? token;

  @override
  Future<void> clearToken() async {
    token = null;
  }

  @override
  Future<String?> readToken() async {
    return token;
  }

  @override
  Future<void> writeToken(String accessToken) async {
    token = accessToken;
  }
}

class _ControllerFakeApi implements RouteMatesApiClient {
  String? token;

  @override
  Future<RoutePost> createRoute(Map<String, dynamic> payload) {
    throw UnimplementedError();
  }

  @override
  Future<List<DiscoveredRoute>> discoverRoutes({
    String? origin,
    String? destination,
    DateTime? travelDate,
  }) async {
    return const <DiscoveredRoute>[];
  }

  @override
  Future<AppUser> getCurrentAuthUser() async {
    if (token == null) {
      throw Exception('Missing token');
    }

    return const AppUser(
      id: 'user-1',
      email: 'restore@example.com',
      name: 'Restore User',
    );
  }

  @override
  Future<UserProfile> getMyProfile() async {
    return const UserProfile(
      id: 'user-1',
      email: 'restore@example.com',
      name: 'Restore User',
      isProfileComplete: false,
    );
  }

  @override
  Future<List<RoutePost>> getMyRoutes() async {
    return const <RoutePost>[];
  }

  @override
  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    return const AuthSession(
      accessToken: 'token-login',
      user: AppUser(id: 'user-2', email: 'login@example.com', name: 'Login User'),
    );
  }

  @override
  Future<AuthSession> register({
    required String email,
    required String name,
    required String password,
  }) {
    throw UnimplementedError();
  }

  @override
  void setAccessToken(String? token) {
    this.token = token;
  }

  @override
  Future<UserProfile> updateMyProfile(Map<String, dynamic> payload) {
    throw UnimplementedError();
  }
}
