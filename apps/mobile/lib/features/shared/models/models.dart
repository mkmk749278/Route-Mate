class AppUser {
  const AppUser({
    required this.id,
    required this.email,
    required this.name,
  });

  final String id;
  final String email;
  final String name;

  factory AppUser.fromJson(Map<String, dynamic> json) {
    return AppUser(
      id: json['id'] as String,
      email: json['email'] as String,
      name: json['name'] as String,
    );
  }
}

class AuthSession {
  const AuthSession({
    required this.accessToken,
    required this.user,
  });

  final String accessToken;
  final AppUser user;

  factory AuthSession.fromJson(Map<String, dynamic> json) {
    return AuthSession(
      accessToken: json['accessToken'] as String,
      user: AppUser.fromJson(json['user'] as Map<String, dynamic>),
    );
  }
}

class UserProfile {
  const UserProfile({
    required this.id,
    required this.email,
    required this.name,
    this.phone,
    this.city,
    this.gender,
    this.bio,
    this.avatarUrl,
    required this.isProfileComplete,
  });

  final String id;
  final String email;
  final String name;
  final String? phone;
  final String? city;
  final String? gender;
  final String? bio;
  final String? avatarUrl;
  final bool isProfileComplete;

  factory UserProfile.fromJson(Map<String, dynamic> json) {
    return UserProfile(
      id: json['id'] as String,
      email: json['email'] as String,
      name: json['name'] as String,
      phone: json['phone'] as String?,
      city: json['city'] as String?,
      gender: json['gender'] as String?,
      bio: json['bio'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
      isProfileComplete: json['isProfileComplete'] as bool? ?? false,
    );
  }
}

class RoutePost {
  const RoutePost({
    required this.id,
    required this.userId,
    required this.origin,
    required this.destination,
    required this.travelDate,
    required this.preferredDepartureTime,
    this.seatCount,
    this.notes,
    required this.status,
  });

  final String id;
  final String userId;
  final String origin;
  final String destination;
  final DateTime travelDate;
  final String preferredDepartureTime;
  final int? seatCount;
  final String? notes;
  final String status;

  factory RoutePost.fromJson(Map<String, dynamic> json) {
    return RoutePost(
      id: json['id'] as String,
      userId: json['userId'] as String,
      origin: json['origin'] as String,
      destination: json['destination'] as String,
      travelDate: DateTime.parse(json['travelDate'] as String),
      preferredDepartureTime: json['preferredDepartureTime'] as String,
      seatCount: json['seatCount'] as int?,
      notes: json['notes'] as String?,
      status: json['status'] as String,
    );
  }
}

class RouteOwner {
  const RouteOwner({
    required this.id,
    required this.name,
    this.city,
    this.avatarUrl,
  });

  final String id;
  final String name;
  final String? city;
  final String? avatarUrl;

  factory RouteOwner.fromJson(Map<String, dynamic> json) {
    return RouteOwner(
      id: json['id'] as String,
      name: json['name'] as String,
      city: json['city'] as String?,
      avatarUrl: json['avatarUrl'] as String?,
    );
  }
}

class DiscoveredRoute extends RoutePost {
  const DiscoveredRoute({
    required super.id,
    required super.userId,
    required super.origin,
    required super.destination,
    required super.travelDate,
    required super.preferredDepartureTime,
    super.seatCount,
    super.notes,
    required super.status,
    required this.owner,
  });

  final RouteOwner owner;

  factory DiscoveredRoute.fromJson(Map<String, dynamic> json) {
    return DiscoveredRoute(
      id: json['id'] as String,
      userId: json['userId'] as String,
      origin: json['origin'] as String,
      destination: json['destination'] as String,
      travelDate: DateTime.parse(json['travelDate'] as String),
      preferredDepartureTime: json['preferredDepartureTime'] as String,
      seatCount: json['seatCount'] as int?,
      notes: json['notes'] as String?,
      status: json['status'] as String,
      owner: RouteOwner.fromJson(json['owner'] as Map<String, dynamic>),
    );
  }
}
