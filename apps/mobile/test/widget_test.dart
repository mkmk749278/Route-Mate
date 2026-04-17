import 'package:flutter_test/flutter_test.dart';
import 'package:route_mates_mobile/app/app.dart';
import 'package:route_mates_mobile/core/api/route_mates_api.dart';
import 'package:route_mates_mobile/core/storage/auth_token_storage.dart';
import 'package:route_mates_mobile/features/shared/models/models.dart';
import 'package:route_mates_mobile/features/shared/state/app_controller.dart';

void main() {
  testWidgets('shows auth tabs when unauthenticated', (tester) async {
    final api = _FakeApi();
    final controller = AppController(
      api: api,
      tokenStorage: _InMemoryTokenStorage(),
    );

    await controller.initialize();
    await tester.pumpWidget(RouteMatesApp(controller: controller));

    expect(find.text('Login'), findsOneWidget);
    expect(find.text('Register'), findsOneWidget);
  });

  testWidgets('shows dashboard tabs when token session restores', (tester) async {
    final api = _FakeApi();
    final controller = AppController(
      api: api,
      tokenStorage: _InMemoryTokenStorage(initialToken: 'token-1'),
    );

    await controller.initialize();
    await tester.pumpWidget(RouteMatesApp(controller: controller));

    expect(find.text('Profile'), findsOneWidget);
    expect(find.text('Create Route'), findsOneWidget);
    expect(find.text('Discover'), findsOneWidget);
  });
}

class _InMemoryTokenStorage implements AuthTokenStorage {
  _InMemoryTokenStorage({this.initialToken}) : _token = initialToken;

  final String? initialToken;
  String? _token;

  @override
  Future<void> clearToken() async {
    _token = null;
  }

  @override
  Future<String?> readToken() async {
    return _token;
  }

  @override
  Future<void> writeToken(String token) async {
    _token = token;
  }
}

class _FakeApi implements RouteMatesApiClient {
  String? _accessToken;

  @override
  Future<RoutePost> createRoute(Map<String, dynamic> payload) async {
    return RoutePost(
      id: 'route-1',
      userId: 'user-1',
      origin: payload['origin'] as String,
      destination: payload['destination'] as String,
      travelDate: DateTime.parse(payload['travelDate'] as String),
      preferredDepartureTime: payload['preferredDepartureTime'] as String,
      seatCount: payload['seatCount'] as int?,
      notes: payload['notes'] as String?,
      status: 'active',
    );
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
    if (_accessToken == null) {
      throw Exception('missing token');
    }

    return const AppUser(id: 'user-1', email: 'test@example.com', name: 'Test');
  }

  @override
  Future<UserProfile> getMyProfile() async {
    return const UserProfile(
      id: 'user-1',
      email: 'test@example.com',
      name: 'Test',
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
      accessToken: 'token-1',
      user: AppUser(id: 'user-1', email: 'test@example.com', name: 'Test'),
    );
  }

  @override
  Future<AuthSession> register({
    required String email,
    required String name,
    required String password,
  }) async {
    return AuthSession(
      accessToken: 'token-1',
      user: AppUser(id: 'user-1', email: email, name: name),
    );
  }

  @override
  void setAccessToken(String? token) {
    _accessToken = token;
  }

  @override
  Future<UserProfile> updateMyProfile(Map<String, dynamic> payload) async {
    return UserProfile(
      id: 'user-1',
      email: 'test@example.com',
      name: payload['name'] as String? ?? 'Test',
      phone: payload['phone'] as String?,
      city: payload['city'] as String?,
      gender: payload['gender'] as String?,
      bio: payload['bio'] as String?,
      avatarUrl: payload['avatarUrl'] as String?,
      isProfileComplete: false,
    );
  }
}
