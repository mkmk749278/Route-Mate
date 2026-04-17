import 'package:route_mates_mobile/core/api/api_client.dart';
import 'package:route_mates_mobile/features/shared/models/models.dart';

abstract class RouteMatesApiClient {
  void setAccessToken(String? token);
  Future<AuthSession> register({
    required String email,
    required String name,
    required String password,
  });
  Future<AuthSession> login({
    required String email,
    required String password,
  });
  Future<AppUser> getCurrentAuthUser();
  Future<UserProfile> getMyProfile();
  Future<UserProfile> updateMyProfile(Map<String, dynamic> payload);
  Future<RoutePost> createRoute(Map<String, dynamic> payload);
  Future<List<RoutePost>> getMyRoutes();
  Future<List<DiscoveredRoute>> discoverRoutes({
    String? origin,
    String? destination,
    DateTime? travelDate,
  });
  Future<RouteInterest> createRouteInterest({required String routePostId});
  Future<List<RouteInterest>> getIncomingRouteInterests();
  Future<List<RouteInterest>> getOutgoingRouteInterests();
  Future<RouteInterest> ownerDecisionRouteInterest({
    required String routeInterestId,
    required String status,
  });
}

class RouteMatesApi implements RouteMatesApiClient {
  RouteMatesApi(this._client);

  final ApiClient _client;

  @override
  void setAccessToken(String? token) {
    _client.setAccessToken(token);
  }

  @override
  Future<AuthSession> register({
    required String email,
    required String name,
    required String password,
  }) async {
    final json = await _client.post(
      '/auth/register',
      authenticated: false,
      body: {
        'email': email,
        'name': name,
        'password': password,
      },
    );

    return AuthSession.fromJson(json);
  }

  @override
  Future<AuthSession> login({
    required String email,
    required String password,
  }) async {
    final json = await _client.post(
      '/auth/login',
      authenticated: false,
      body: {
        'email': email,
        'password': password,
      },
    );

    return AuthSession.fromJson(json);
  }

  @override
  Future<AppUser> getCurrentAuthUser() async {
    final json = await _client.get('/auth/me');
    return AppUser.fromJson(json['user'] as Map<String, dynamic>);
  }

  @override
  Future<UserProfile> getMyProfile() async {
    final json = await _client.get('/users/me');
    return UserProfile.fromJson(json['user'] as Map<String, dynamic>);
  }

  @override
  Future<UserProfile> updateMyProfile(Map<String, dynamic> payload) async {
    final json = await _client.patch('/users/me', body: payload);
    return UserProfile.fromJson(json['user'] as Map<String, dynamic>);
  }

  @override
  Future<RoutePost> createRoute(Map<String, dynamic> payload) async {
    final json = await _client.post('/routes', body: payload);
    return RoutePost.fromJson(json['route'] as Map<String, dynamic>);
  }

  @override
  Future<List<RoutePost>> getMyRoutes() async {
    final json = await _client.get('/routes/me');
    final routes = json['routes'] as List<dynamic>? ?? <dynamic>[];
    return routes
        .map((item) => RoutePost.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<List<DiscoveredRoute>> discoverRoutes({
    String? origin,
    String? destination,
    DateTime? travelDate,
  }) async {
    final query = <String, String>{};

    if (origin != null && origin.trim().isNotEmpty) {
      query['origin'] = origin.trim();
    }

    if (destination != null && destination.trim().isNotEmpty) {
      query['destination'] = destination.trim();
    }

    if (travelDate != null) {
      query['travelDate'] = travelDate.toUtc().toIso8601String();
    }

    final json = await _client.get('/routes/discover', query: query);
    final routes = json['routes'] as List<dynamic>? ?? <dynamic>[];

    return routes
        .map((item) => DiscoveredRoute.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<RouteInterest> createRouteInterest({required String routePostId}) async {
    final json = await _client.post(
      '/route-interests',
      body: {'routePostId': routePostId},
    );
    return RouteInterest.fromJson(json['interest'] as Map<String, dynamic>);
  }

  @override
  Future<List<RouteInterest>> getIncomingRouteInterests() async {
    final json = await _client.get('/route-interests/incoming');
    final interests = json['interests'] as List<dynamic>? ?? <dynamic>[];
    return interests
        .map((item) => RouteInterest.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<List<RouteInterest>> getOutgoingRouteInterests() async {
    final json = await _client.get('/route-interests/outgoing');
    final interests = json['interests'] as List<dynamic>? ?? <dynamic>[];
    return interests
        .map((item) => RouteInterest.fromJson(item as Map<String, dynamic>))
        .toList(growable: false);
  }

  @override
  Future<RouteInterest> ownerDecisionRouteInterest({
    required String routeInterestId,
    required String status,
  }) async {
    final json = await _client.patch(
      '/route-interests/$routeInterestId/owner-decision',
      body: {'status': status},
    );
    return RouteInterest.fromJson(json['interest'] as Map<String, dynamic>);
  }
}
