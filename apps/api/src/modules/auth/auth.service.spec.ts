import { JwtModule } from '@nestjs/jwt';
import { Test, TestingModule } from '@nestjs/testing';
import { AuthService } from './auth.service';

describe('AuthService', () => {
  let service: AuthService;

  beforeEach(async () => {
    const module: TestingModule = await Test.createTestingModule({
      imports: [
        JwtModule.register({
          secret: 'test-secret-for-auth-service',
          signOptions: { expiresIn: '1h' },
        }),
      ],
      providers: [AuthService],
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
