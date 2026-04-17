import 'package:flutter/foundation.dart';
import 'package:route_mates_mobile/core/api/route_mates_api.dart';
import 'package:route_mates_mobile/core/storage/auth_token_storage.dart';
import 'package:route_mates_mobile/features/shared/models/models.dart';

class AppController extends ChangeNotifier {
  AppController({
    required RouteMatesApiClient api,
    required AuthTokenStorage tokenStorage,
  })  : _api = api,
        _tokenStorage = tokenStorage;

  final RouteMatesApiClient _api;
  final AuthTokenStorage _tokenStorage;

  String? _accessToken;
  AppUser? _currentUser;
  UserProfile? _profile;
  List<RoutePost> _myRoutes = const <RoutePost>[];
  List<DiscoveredRoute> _discoveredRoutes = const <DiscoveredRoute>[];
  List<RouteInterest> _incomingInterests = const <RouteInterest>[];
  List<RouteInterest> _outgoingInterests = const <RouteInterest>[];
  bool _isRestoringSession = true;
  bool _isSubmitting = false;

  bool get isRestoringSession => _isRestoringSession;
  bool get isSubmitting => _isSubmitting;
  bool get isAuthenticated => _accessToken != null && _currentUser != null;
  AppUser? get currentUser => _currentUser;
  UserProfile? get profile => _profile;
  List<RoutePost> get myRoutes => _myRoutes;
  List<DiscoveredRoute> get discoveredRoutes => _discoveredRoutes;
  List<RouteInterest> get incomingInterests => _incomingInterests;
  List<RouteInterest> get outgoingInterests => _outgoingInterests;

  Future<void> initialize() async {
    _isRestoringSession = true;
    notifyListeners();

    final storedToken = await _tokenStorage.readToken();

    if (storedToken == null) {
      _isRestoringSession = false;
      notifyListeners();
      return;
    }

    _accessToken = storedToken;
    _api.setAccessToken(storedToken);

    try {
      _currentUser = await _api.getCurrentAuthUser();
      await fetchProfile();
    } catch (_) {
      await logout();
    }

    _isRestoringSession = false;
    notifyListeners();
  }

  Future<void> register({
    required String email,
    required String name,
    required String password,
  }) async {
    await _runSubmitting(() async {
      final session = await _api.register(
        email: email,
        name: name,
        password: password,
      );
      await _setSession(session);
      await fetchProfile();
    });
  }

  Future<void> login({
    required String email,
    required String password,
  }) async {
    await _runSubmitting(() async {
      final session = await _api.login(email: email, password: password);
      await _setSession(session);
      await fetchProfile();
    });
  }

  Future<void> logout() async {
    _accessToken = null;
    _api.setAccessToken(null);
    _currentUser = null;
    _profile = null;
    _myRoutes = const <RoutePost>[];
    _discoveredRoutes = const <DiscoveredRoute>[];
    _incomingInterests = const <RouteInterest>[];
    _outgoingInterests = const <RouteInterest>[];
    await _tokenStorage.clearToken();
    notifyListeners();
  }

  Future<UserProfile> fetchProfile() async {
    final userProfile = await _api.getMyProfile();
    _profile = userProfile;
    notifyListeners();
    return userProfile;
  }

  Future<void> updateProfile(Map<String, dynamic> payload) async {
    await _runSubmitting(() async {
      _profile = await _api.updateMyProfile(payload);
    });
  }

  Future<void> createRoute(Map<String, dynamic> payload) async {
    await _runSubmitting(() async {
      final route = await _api.createRoute(payload);
      _myRoutes = <RoutePost>[route, ..._myRoutes];
    });
  }

  Future<void> fetchMyRoutes() async {
    _myRoutes = await _api.getMyRoutes();
    notifyListeners();
  }

  Future<void> discoverRoutes({
    String? origin,
    String? destination,
    DateTime? travelDate,
  }) async {
    _discoveredRoutes = await _api.discoverRoutes(
      origin: origin,
      destination: destination,
      travelDate: travelDate,
    );
    notifyListeners();
  }

  Future<void> createRouteInterest(String routePostId) async {
    await _runSubmitting(() async {
      final interest = await _api.createRouteInterest(routePostId: routePostId);
      _outgoingInterests = <RouteInterest>[interest, ..._outgoingInterests];
    });
  }

  Future<void> fetchIncomingInterests() async {
    _incomingInterests = await _api.getIncomingRouteInterests();
    notifyListeners();
  }

  Future<void> fetchOutgoingInterests() async {
    _outgoingInterests = await _api.getOutgoingRouteInterests();
    notifyListeners();
  }

  Future<void> ownerDecisionRouteInterest({
    required String routeInterestId,
    required String status,
  }) async {
    await _runSubmitting(() async {
      final updated = await _api.ownerDecisionRouteInterest(
        routeInterestId: routeInterestId,
        status: status,
      );
      _incomingInterests = _incomingInterests
          .map((interest) => interest.id == updated.id ? updated : interest)
          .toList(growable: false);
      _outgoingInterests = _outgoingInterests
          .map((interest) => interest.id == updated.id ? updated : interest)
          .toList(growable: false);
    });
  }

  Future<void> _setSession(AuthSession session) async {
    _accessToken = session.accessToken;
    _currentUser = session.user;
    _api.setAccessToken(session.accessToken);
    await _tokenStorage.writeToken(session.accessToken);
    notifyListeners();
  }

  Future<void> _runSubmitting(Future<void> Function() action) async {
    _isSubmitting = true;
    notifyListeners();

    try {
      await action();
    } finally {
      _isSubmitting = false;
      notifyListeners();
    }
  }
}
