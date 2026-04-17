import { JwtModule } from '@nestjs/jwt';
import { Test, TestingModule } from '@nestjs/testing';
import { PrismaService } from '../../prisma/prisma.service';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;
  const users = new Map<
    string,
    { id: string; email: string; name: string; passwordHash: string }
  >();
  const prismaMock = {
    user: {
      findUnique: jest.fn(
        (params: { where: { id?: string; email?: string } }) => {
          if (params.where.id) {
            return Promise.resolve(
              Array.from(users.values()).find(
                (candidate) => candidate.id === params.where.id,
              ) ?? null,
            );
          }

          if (!params.where.email) {
            return Promise.resolve(null);
          }

          return Promise.resolve(users.get(params.where.email) ?? null);
        },
      ),
      create: jest.fn(
        (params: {
          data: { email: string; name: string; passwordHash: string };
        }) => {
          const created = {
            id: crypto.randomUUID(),
            ...params.data,
          };
          users.set(created.email, created);
          return Promise.resolve(created);
        },
      ),
    },
  };

  beforeEach(async () => {
    users.clear();
    jest.clearAllMocks();

    const module: TestingModule = await Test.createTestingModule({
      imports: [
        JwtModule.register({
          secret: 'test-secret-for-auth-service',
          signOptions: { expiresIn: '1h' },
        }),
      ],
      providers: [
        AuthService,
        {
          provide: PrismaService,
          useValue: prismaMock,
        },
      ],
    }).compile();

    service = module.get<AuthService>(AuthService);
  });

  it('registers user with hashed password and logs in successfully', async () => {
    const registerResult = await service.register({
      email: 'test@example.com',
      name: 'Test User',
      password: 'Password123',
    });

    expect(registerResult.user.email).toBe('test@example.com');
    expect(registerResult.user.name).toBe('Test User');
    expect(registerResult.user.id).toBeTruthy();
    expect(registerResult.accessToken).toBeTruthy();
    expect(JSON.stringify(registerResult)).not.toContain('Password123');

    const loginResult = await service.login({
      email: 'test@example.com',
      password: 'Password123',
    });

    expect(loginResult.user.email).toBe('test@example.com');
    expect(loginResult.accessToken).toBeTruthy();
  });
});
