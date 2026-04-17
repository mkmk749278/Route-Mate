import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from './../src/app.module';
import { PrismaService } from './../src/prisma/prisma.service';

describe('AppController (e2e)', () => {
  let app: INestApplication<App>;
  type MockUser = {
    id: string;
    email: string;
    name: string;
    passwordHash: string;
    phone: string | null;
    city: string | null;
    gender: string | null;
    bio: string | null;
    avatarUrl: string | null;
    isProfileComplete: boolean;
    createdAt: Date;
    updatedAt: Date;
  };
  type AuthResponse = {
    accessToken: string;
    user: {
      id: string;
      email: string;
      name: string;
    };
  };
  type MockRoutePost = {
    id: string;
    userId: string;
    origin: string;
    destination: string;
    travelDate: Date;
    preferredDepartureTime: string;
    seatCount: number | null;
    notes: string | null;
    status: string;
    createdAt: Date;
    updatedAt: Date;
  };

  beforeAll(async () => {
    const users = new Map<string, MockUser>();
    const routePosts = new Map<string, MockRoutePost>();
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(PrismaService)
      .useValue({
        user: {
          findUnique: (params: {
            where: { id?: string; email?: string };
            select?: Record<string, boolean>;
          }) => {
            if (params.where.id) {
              const userById = Array.from(users.values()).find(
                (candidate) => candidate.id === params.where.id,
              );

              if (!userById) {
                return Promise.resolve(null);
              }

              if (params.select) {
                return Promise.resolve(
                  Object.fromEntries(
                    Object.keys(params.select).map((key) => [
                      key,
                      userById[key as keyof MockUser],
                    ]),
                  ),
                );
              }

              return Promise.resolve(userById);
            }

            if (!params.where.email) {
              return Promise.resolve(null);
            }

            return Promise.resolve(users.get(params.where.email) ?? null);
          },
          create: (params: {
            data: { email: string; name: string; passwordHash: string };
          }) => {
            const now = new Date();
            const created = {
              id: crypto.randomUUID(),
              ...params.data,
              phone: null,
              city: null,
              gender: null,
              bio: null,
              avatarUrl: null,
              isProfileComplete: false,
              createdAt: now,
              updatedAt: now,
            };
            users.set(created.email, created);
            return Promise.resolve(created);
          },
          update: (params: {
            where: { id: string };
            data: Record<string, string | boolean>;
            select?: Record<string, boolean>;
          }) => {
            const current = Array.from(users.values()).find(
              (candidate) => candidate.id === params.where.id,
            );

            if (!current) {
              return Promise.resolve(null);
            }

            const sanitizedData = Object.fromEntries(
              Object.entries(params.data).filter(
                ([, value]) => value !== undefined,
              ),
            );

            const updated: MockUser = {
              ...current,
              ...sanitizedData,
              updatedAt: new Date(),
            };
            users.set(updated.email, updated);

            if (params.select) {
              return Promise.resolve(
                Object.fromEntries(
                  Object.keys(params.select).map((key) => [
                    key,
                    updated[key as keyof MockUser],
                  ]),
                ),
              );
            }

            return Promise.resolve(updated);
          },
        },
        routePost: {
          create: (params: {
            data: {
              userId: string;
              origin: string;
              destination: string;
              travelDate: Date;
              preferredDepartureTime: string;
              seatCount?: number;
              notes?: string;
            };
            select?: Record<string, boolean>;
          }) => {
            const now = new Date();
            const created: MockRoutePost = {
              id: crypto.randomUUID(),
              userId: params.data.userId,
              origin: params.data.origin,
              destination: params.data.destination,
              travelDate: params.data.travelDate,
              preferredDepartureTime: params.data.preferredDepartureTime,
              seatCount: params.data.seatCount ?? null,
              notes: params.data.notes ?? null,
              status: 'active',
              createdAt: now,
              updatedAt: now,
            };
            routePosts.set(created.id, created);

            if (params.select) {
              return Promise.resolve(
                Object.fromEntries(
                  Object.keys(params.select).map((key) => [
                    key,
                    created[key as keyof MockRoutePost],
                  ]),
                ),
              );
            }

            return Promise.resolve(created);
          },
          findMany: (params: {
            where?: {
              userId?: string | { not: string };
              origin?: { contains: string; mode: 'insensitive' };
              destination?: { contains: string; mode: 'insensitive' };
              travelDate?: { gte: Date; lt: Date };
            };
            orderBy?:
              | Array<Record<string, 'asc' | 'desc'>>
              | Record<string, 'asc' | 'desc'>;
            select?: Record<
              string,
              boolean | { select: Record<string, boolean> }
            >;
          }) => {
            const filtered = Array.from(routePosts.values()).filter((route) => {
              if (typeof params.where?.userId === 'string') {
                return route.userId === params.where.userId;
              }

              if (
                typeof params.where?.userId === 'object' &&
                params.where.userId?.not
              ) {
                if (route.userId === params.where.userId.not) {
                  return false;
                }
              }

              if (params.where?.origin?.contains) {
                const query = params.where.origin.contains.toLowerCase();
                if (!route.origin.toLowerCase().includes(query)) {
                  return false;
                }
              }

              if (params.where?.destination?.contains) {
                const query = params.where.destination.contains.toLowerCase();
                if (!route.destination.toLowerCase().includes(query)) {
                  return false;
                }
              }

              if (params.where?.travelDate) {
                const routeTime = route.travelDate.getTime();
                if (
                  routeTime < params.where.travelDate.gte.getTime() ||
                  routeTime >= params.where.travelDate.lt.getTime()
                ) {
                  return false;
                }
              }

              return true;
            });

            const sorted = filtered.sort((a, b) => {
              if (Array.isArray(params.orderBy)) {
                const travelDateDelta =
                  a.travelDate.getTime() - b.travelDate.getTime();
                if (travelDateDelta !== 0) {
                  return travelDateDelta;
                }
                return b.createdAt.getTime() - a.createdAt.getTime();
              }

              return b.createdAt.getTime() - a.createdAt.getTime();
            });

            if (params.select) {
              return Promise.resolve(
                sorted.map((route) => {
                  const selectedRoute = Object.entries(params.select).reduce<
                    Record<string, unknown>
                  >((accumulator, [key, value]) => {
                    if (
                      key === 'user' &&
                      typeof value === 'object' &&
                      'select' in value
                    ) {
                      const owner = Array.from(users.values()).find(
                        (candidate) => candidate.id === route.userId,
                      );

                      accumulator[key] = owner
                        ? Object.keys(value.select).reduce<
                            Record<string, unknown>
                          >((ownerAccumulator, ownerKey) => {
                            ownerAccumulator[ownerKey] =
                              owner[ownerKey as keyof MockUser];
                            return ownerAccumulator;
                          }, {})
                        : null;
                      return accumulator;
                    }

                    accumulator[key] = route[key as keyof MockRoutePost];
                    return accumulator;
                  }, {});

                  return selectedRoute;
                }),
              );
            }

            return Promise.resolve(sorted);
          },
        },
      })
      .compile();

    app = moduleFixture.createNestApplication();
    app.useGlobalPipes(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    );
    await app.init();
  });

  it('/health (GET)', () => {
    return request(app.getHttpServer())
      .get('/health')
      .expect(200)
      .expect(({ body }) => {
        const health = body as {
          status?: string;
          service?: string;
          timestamp?: string;
        };
        expect(health.status).toBe('healthy');
        expect(health.service).toBe('route-mates-api');
        expect(health.timestamp).toBeTruthy();
      });
  });

  it('auth flow: register -> login -> me', async () => {
    const registerResponse = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'e2e@example.com',
        name: 'E2E User',
        password: 'StrongPass123',
      })
      .expect(201);
    const registerBody = registerResponse.body as AuthResponse;

    expect(registerBody.user.email).toBe('e2e@example.com');
    expect(registerBody.accessToken).toBeTruthy();

    const loginResponse = await request(app.getHttpServer())
      .post('/auth/login')
      .send({
        email: 'e2e@example.com',
        password: 'StrongPass123',
      })
      .expect(201);
    const loginBody = loginResponse.body as AuthResponse;

    expect(loginBody.accessToken).toBeTruthy();

    await request(app.getHttpServer())
      .get('/auth/me')
      .set('Authorization', `Bearer ${loginBody.accessToken}`)
      .expect(200)
      .expect(({ body }) => {
        const meBody = body as { user?: { email?: string } };
        expect(meBody.user?.email).toBe('e2e@example.com');
      });
  });

  it('rejects invalid register payload', async () => {
    await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'bad-email',
        name: 'A',
        password: '123',
      })
      .expect(400);
  });

  it('rejects /auth/me without token', async () => {
    await request(app.getHttpServer()).get('/auth/me').expect(401);
  });

  it('profile flow: get me -> update me', async () => {
    const registerResponse = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'profile@example.com',
        name: 'Profile User',
        password: 'StrongPass123',
      })
      .expect(201);
    const authBody = registerResponse.body as AuthResponse;

    await request(app.getHttpServer())
      .get('/users/me')
      .set('Authorization', `Bearer ${authBody.accessToken}`)
      .expect(200)
      .expect(({ body }) => {
        const meBody = body as {
          user?: {
            email?: string;
            city?: string | null;
            isProfileComplete?: boolean;
          };
        };
        expect(meBody.user?.email).toBe('profile@example.com');
        expect(meBody.user?.city).toBeNull();
        expect(meBody.user?.isProfileComplete).toBe(false);
      });

    await request(app.getHttpServer())
      .patch('/users/me')
      .set('Authorization', `Bearer ${authBody.accessToken}`)
      .send({
        city: 'Bengaluru',
        bio: 'I commute daily and share routes.',
        phone: '+14155550100',
        gender: 'prefer_not_to_say',
        avatarUrl: 'https://example.com/avatar.png',
      })
      .expect(200)
      .expect(({ body }) => {
        const updatedBody = body as {
          user?: {
            city?: string | null;
            bio?: string | null;
            phone?: string | null;
            gender?: string | null;
            avatarUrl?: string | null;
            isProfileComplete?: boolean;
          };
        };

        expect(updatedBody.user?.city).toBe('Bengaluru');
        expect(updatedBody.user?.bio).toBe('I commute daily and share routes.');
        expect(updatedBody.user?.phone).toBe('+14155550100');
        expect(updatedBody.user?.gender).toBe('prefer_not_to_say');
        expect(updatedBody.user?.avatarUrl).toBe(
          'https://example.com/avatar.png',
        );
        expect(updatedBody.user?.isProfileComplete).toBe(true);
      });
  });

  it('rejects /users/me without token', async () => {
    await request(app.getHttpServer()).get('/users/me').expect(401);
  });

  it('rejects invalid /users/me patch payload', async () => {
    const registerResponse = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'profile-invalid@example.com',
        name: 'Profile Invalid',
        password: 'StrongPass123',
      })
      .expect(201);
    const authBody = registerResponse.body as AuthResponse;

    await request(app.getHttpServer())
      .patch('/users/me')
      .set('Authorization', `Bearer ${authBody.accessToken}`)
      .send({
        gender: 'invalid',
      })
      .expect(400);
  });

  it('routes flow: create route -> list my routes only', async () => {
    const riderRegister = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'routes-rider@example.com',
        name: 'Routes Rider',
        password: 'StrongPass123',
      })
      .expect(201);
    const riderAuth = riderRegister.body as AuthResponse;

    const otherRegister = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'routes-other@example.com',
        name: 'Routes Other',
        password: 'StrongPass123',
      })
      .expect(201);
    const otherAuth = otherRegister.body as AuthResponse;

    await request(app.getHttpServer())
      .post('/routes')
      .set('Authorization', `Bearer ${otherAuth.accessToken}`)
      .send({
        origin: 'Miyapur',
        destination: 'HITEC City',
        travelDate: '2026-05-01T00:00:00.000Z',
        preferredDepartureTime: '08:15',
      })
      .expect(201);

    await request(app.getHttpServer())
      .post('/routes')
      .set('Authorization', `Bearer ${riderAuth.accessToken}`)
      .send({
        origin: 'Kukatpally',
        destination: 'Gachibowli',
        travelDate: '2026-05-02T00:00:00.000Z',
        preferredDepartureTime: '09:30',
        seatCount: 2,
        notes: 'Can start +/- 15 mins',
      })
      .expect(201)
      .expect(({ body }) => {
        const createBody = body as {
          route?: {
            origin?: string;
            destination?: string;
            status?: string;
            seatCount?: number | null;
          };
        };
        expect(createBody.route?.origin).toBe('Kukatpally');
        expect(createBody.route?.destination).toBe('Gachibowli');
        expect(createBody.route?.status).toBe('active');
        expect(createBody.route?.seatCount).toBe(2);
      });

    await request(app.getHttpServer())
      .get('/routes/me')
      .set('Authorization', `Bearer ${riderAuth.accessToken}`)
      .expect(200)
      .expect(({ body }) => {
        const meRoutesBody = body as {
          routes?: Array<{ origin?: string; destination?: string }>;
        };
        expect(meRoutesBody.routes).toHaveLength(1);
        expect(meRoutesBody.routes?.[0]?.origin).toBe('Kukatpally');
        expect(meRoutesBody.routes?.[0]?.destination).toBe('Gachibowli');
      });
  });

  it('routes discover flow: excludes own posts and applies filters', async () => {
    const requesterRegister = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'discover-requester@example.com',
        name: 'Discover Requester',
        password: 'StrongPass123',
      })
      .expect(201);
    const requesterAuth = requesterRegister.body as AuthResponse;

    const ownerOneRegister = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'discover-owner-one@example.com',
        name: 'Discover Owner One',
        password: 'StrongPass123',
      })
      .expect(201);
    const ownerOneAuth = ownerOneRegister.body as AuthResponse;

    const ownerTwoRegister = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'discover-owner-two@example.com',
        name: 'Discover Owner Two',
        password: 'StrongPass123',
      })
      .expect(201);
    const ownerTwoAuth = ownerTwoRegister.body as AuthResponse;

    await request(app.getHttpServer())
      .patch('/users/me')
      .set('Authorization', `Bearer ${ownerOneAuth.accessToken}`)
      .send({
        city: 'Hyderabad',
        avatarUrl: 'https://example.com/discover-owner-one.png',
      })
      .expect(200);

    await request(app.getHttpServer())
      .post('/routes')
      .set('Authorization', `Bearer ${requesterAuth.accessToken}`)
      .send({
        origin: 'Kondapur',
        destination: 'Madhapur',
        travelDate: '2026-06-10T00:00:00.000Z',
        preferredDepartureTime: '08:30',
      })
      .expect(201);

    await request(app.getHttpServer())
      .post('/routes')
      .set('Authorization', `Bearer ${ownerOneAuth.accessToken}`)
      .send({
        origin: 'Miyapur',
        destination: 'HITEC City',
        travelDate: '2026-06-12T00:00:00.000Z',
        preferredDepartureTime: '09:00',
        seatCount: 3,
      })
      .expect(201);

    await request(app.getHttpServer())
      .post('/routes')
      .set('Authorization', `Bearer ${ownerTwoAuth.accessToken}`)
      .send({
        origin: 'Secunderabad',
        destination: 'Gachibowli',
        travelDate: '2026-06-12T00:00:00.000Z',
        preferredDepartureTime: '08:45',
      })
      .expect(201);

    const discoverResponse = await request(app.getHttpServer())
      .get('/routes/discover')
      .query({ travelDate: '2026-06-12' })
      .set('Authorization', `Bearer ${requesterAuth.accessToken}`)
      .expect(200);

    const discoverBody = discoverResponse.body as {
      routes?: Array<{
        userId?: string;
        owner?: {
          name?: string;
          city?: string | null;
          avatarUrl?: string | null;
        };
      }>;
    };
    expect(discoverBody.routes).toHaveLength(2);
    expect(
      discoverBody.routes?.every(
        (route) => route.userId !== requesterAuth.user.id,
      ),
    ).toBe(true);
    expect(discoverBody.routes?.[0]?.owner?.name).toBeTruthy();

    await request(app.getHttpServer())
      .get('/routes/discover')
      .query({
        origin: 'miyapur',
        destination: 'hitec',
        travelDate: '2026-06-12',
      })
      .set('Authorization', `Bearer ${requesterAuth.accessToken}`)
      .expect(200)
      .expect(({ body }) => {
        const filteredBody = body as {
          routes?: Array<{
            origin?: string;
            destination?: string;
            owner?: {
              name?: string;
              city?: string | null;
              avatarUrl?: string | null;
            };
          }>;
        };
        expect(filteredBody.routes).toHaveLength(1);
        expect(filteredBody.routes?.[0]?.origin).toBe('Miyapur');
        expect(filteredBody.routes?.[0]?.destination).toBe('HITEC City');
        expect(filteredBody.routes?.[0]?.owner?.name).toBe(
          'Discover Owner One',
        );
        expect(filteredBody.routes?.[0]?.owner?.city).toBe('Hyderabad');
        expect(filteredBody.routes?.[0]?.owner?.avatarUrl).toBe(
          'https://example.com/discover-owner-one.png',
        );
      });
  });

  it('rejects /routes without token', async () => {
    await request(app.getHttpServer())
      .post('/routes')
      .send({
        origin: 'Ameerpet',
        destination: 'Madhapur',
        travelDate: '2026-05-03T00:00:00.000Z',
        preferredDepartureTime: '10:00',
      })
      .expect(401);
  });

  it('rejects /routes/me without token', async () => {
    await request(app.getHttpServer()).get('/routes/me').expect(401);
  });

  it('rejects /routes/discover without token', async () => {
    await request(app.getHttpServer()).get('/routes/discover').expect(401);
  });

  it('rejects invalid /routes payload', async () => {
    const registerResponse = await request(app.getHttpServer())
      .post('/auth/register')
      .send({
        email: 'routes-invalid@example.com',
        name: 'Routes Invalid',
        password: 'StrongPass123',
      })
      .expect(201);
    const authBody = registerResponse.body as AuthResponse;

    await request(app.getHttpServer())
      .post('/routes')
      .set('Authorization', `Bearer ${authBody.accessToken}`)
      .send({
        origin: '',
        destination: 'Madhapur',
        travelDate: 'not-a-date',
        preferredDepartureTime: '9AM',
        seatCount: 0,
      })
      .expect(400);
  });

  afterAll(async () => {
    if (app) {
      await app.close();
    }
  });
});
