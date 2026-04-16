import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';
import { LoginDto } from './dto/login.dto';
import { RegisterDto } from './dto/register.dto';

type AuthUserRecord = {
  id: string;
  email: string;
  name: string;
  passwordHash: string;
};

type SafeAuthUser = Omit<AuthUserRecord, 'passwordHash'>;

@Injectable()
export class AuthService {
  private readonly users = new Map<string, AuthUserRecord>();

  constructor(private readonly jwtService: JwtService) {}

  async register(registerDto: RegisterDto) {
    const normalizedEmail = registerDto.email.toLowerCase();

    if (this.users.has(normalizedEmail)) {
      throw new ConflictException('User with this email already exists');
    }

    const passwordHash = await bcrypt.hash(registerDto.password, 10);
    const user: AuthUserRecord = {
      id: crypto.randomUUID(),
      email: normalizedEmail,
      name: registerDto.name,
      passwordHash,
    };

    this.users.set(normalizedEmail, user);

    return this.buildAuthResponse(user);
  }

  async login(loginDto: LoginDto) {
    const normalizedEmail = loginDto.email.toLowerCase();
    const user = this.users.get(normalizedEmail);

    if (!user) {
      throw new UnauthorizedException('Invalid credentials');
    }

    const isPasswordValid = await bcrypt.compare(
      loginDto.password,
      user.passwordHash,
    );

    if (!isPasswordValid) {
      throw new UnauthorizedException('Invalid credentials');
    }

    return this.buildAuthResponse(user);
  }

  findById(userId: string) {
    for (const user of this.users.values()) {
      if (user.id === userId) {
        return this.toSafeUser(user);
      }
    }

    return undefined;
  }

  private buildAuthResponse(user: AuthUserRecord) {
    const safeUser = this.toSafeUser(user);
    const accessToken = this.jwtService.sign({
      sub: safeUser.id,
      email: safeUser.email,
      name: safeUser.name,
    });

    return {
      accessToken,
      user: safeUser,
    };
  }

  private toSafeUser(user: AuthUserRecord): SafeAuthUser {
    return {
      id: user.id,
      email: user.email,
      name: user.name,
    };
  }
}
