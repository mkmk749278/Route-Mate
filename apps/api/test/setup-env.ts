process.env.JWT_SECRET ??= 'test-jwt-secret-123456';
process.env.JWT_EXPIRES_IN ??= '1h';
process.env.DATABASE_URL ??=
  'postgresql://route_mates:route_mates@localhost:5432/route_mates';
