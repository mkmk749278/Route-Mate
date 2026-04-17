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

  test('create route interest updates outgoing list', () async {
    final api = _ControllerFakeApi();
    final tokenStorage = _MemoryTokenStorage(initialToken: 'stored-token');
    final controller = AppController(api: api, tokenStorage: tokenStorage);

    await controller.initialize();
    await controller.createRouteInterest('route-1');

    expect(controller.outgoingInterests, hasLength(1));
    expect(controller.outgoingInterests.first.routePostId, 'route-1');
    expect(controller.outgoingInterests.first.status, 'pending');
  });

  test('owner decision updates incoming interest status', () async {
    final api = _ControllerFakeApi();
    final tokenStorage = _MemoryTokenStorage(initialToken: 'stored-token');
    final controller = AppController(api: api, tokenStorage: tokenStorage);

    await controller.initialize();
    await controller.fetchIncomingInterests();
    await controller.ownerDecisionRouteInterest(
      routeInterestId: 'interest-incoming-1',
      status: 'accepted',
    );

    expect(controller.incomingInterests, hasLength(1));
    expect(controller.incomingInterests.first.status, 'accepted');
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
  Future<List<RouteInterest>> getIncomingRouteInterests() async {
    return <RouteInterest>[
      RouteInterest(
        id: 'interest-incoming-1',
        routePostId: 'route-9',
        requesterUserId: 'requester-1',
        ownerUserId: 'user-1',
        status: 'pending',
        createdAt: DateTime.parse('2026-04-17T00:00:00.000Z'),
        updatedAt: DateTime.parse('2026-04-17T00:00:00.000Z'),
        route: RouteInterestRouteSummary(
          id: 'route-9',
          origin: 'Miyapur',
          destination: 'HITEC City',
          travelDate: DateTime.parse('2026-04-20T00:00:00.000Z'),
          preferredDepartureTime: '09:00',
        ),
        requester: const RouteInterestUserSummary(
          id: 'requester-1',
          name: 'Requester',
        ),
        owner: const RouteInterestUserSummary(
          id: 'user-1',
          name: 'Owner',
        ),
      ),
    ];
  }

  @override
  Future<List<RouteInterest>> getOutgoingRouteInterests() async {
    return const <RouteInterest>[];
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
  Future<RouteInterest> createRouteInterest({required String routePostId}) async {
    return RouteInterest(
      id: 'interest-created-1',
      routePostId: routePostId,
      requesterUserId: 'user-1',
      ownerUserId: 'owner-1',
      status: 'pending',
      createdAt: DateTime.parse('2026-04-17T00:00:00.000Z'),
      updatedAt: DateTime.parse('2026-04-17T00:00:00.000Z'),
      route: RouteInterestRouteSummary(
        id: routePostId,
        origin: 'A',
        destination: 'B',
        travelDate: DateTime.parse('2026-04-18T00:00:00.000Z'),
        preferredDepartureTime: '08:00',
      ),
      requester: const RouteInterestUserSummary(
        id: 'user-1',
        name: 'Requester',
      ),
      owner: const RouteInterestUserSummary(
        id: 'owner-1',
        name: 'Owner',
      ),
    );
  }

  @override
  void setAccessToken(String? token) {
    this.token = token;
  }

  @override
  Future<UserProfile> updateMyProfile(Map<String, dynamic> payload) {
    throw UnimplementedError();
  }

  @override
  Future<RouteInterest> ownerDecisionRouteInterest({
    required String routeInterestId,
    required String status,
  }) async {
    return RouteInterest(
      id: routeInterestId,
      routePostId: 'route-9',
      requesterUserId: 'requester-1',
      ownerUserId: 'user-1',
      status: status,
      createdAt: DateTime.parse('2026-04-17T00:00:00.000Z'),
      updatedAt: DateTime.parse('2026-04-17T01:00:00.000Z'),
      route: RouteInterestRouteSummary(
        id: 'route-9',
        origin: 'Miyapur',
        destination: 'HITEC City',
        travelDate: DateTime.parse('2026-04-20T00:00:00.000Z'),
        preferredDepartureTime: '09:00',
      ),
      requester: const RouteInterestUserSummary(
        id: 'requester-1',
        name: 'Requester',
      ),
      owner: const RouteInterestUserSummary(
        id: 'user-1',
        name: 'Owner',
      ),
    );
  }
}
