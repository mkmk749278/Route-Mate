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

  beforeAll(async () => {
    const users = new Map<string, MockUser>();
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

            const updated: MockUser = {
              ...current,
              ...params.data,
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

  afterAll(async () => {
    if (app) {
      await app.close();
    }
  });
});
