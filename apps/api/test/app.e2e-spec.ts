import { INestApplication, ValidationPipe } from '@nestjs/common';
import { Test, TestingModule } from '@nestjs/testing';
import request from 'supertest';
import { App } from 'supertest/types';
import { AppModule } from './../src/app.module';
import { PrismaService } from './../src/prisma/prisma.service';

describe('AppController (e2e)', () => {
  let app: INestApplication<App>;
  type AuthResponse = {
    accessToken: string;
    user: {
      id: string;
      email: string;
      name: string;
    };
  };

  beforeAll(async () => {
    const users = new Map<
      string,
      { id: string; email: string; name: string; passwordHash: string }
    >();
    const moduleFixture: TestingModule = await Test.createTestingModule({
      imports: [AppModule],
    })
      .overrideProvider(PrismaService)
      .useValue({
        user: {
          findUnique: (params: {
            where: { id?: string; email?: string };
            select?: { id: true; email: true; name: true };
          }) => {
            if (params.where.id) {
              const userById = Array.from(users.values()).find(
                (candidate) => candidate.id === params.where.id,
              );

              if (!userById) {
                return Promise.resolve(null);
              }

              if (params.select) {
                return Promise.resolve({
                  id: userById.id,
                  email: userById.email,
                  name: userById.name,
                });
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
            const created = {
              id: crypto.randomUUID(),
              ...params.data,
            };
            users.set(created.email, created);
            return Promise.resolve(created);
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

  afterAll(async () => {
    if (app) {
      await app.close();
    }
  });
});
