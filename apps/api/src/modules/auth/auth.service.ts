import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { User } from '@prisma/client';
import { JwtService } from '@nestjs/jwt';
import * as bcrypt from 'bcryptjs';
import { PrismaService } from '../../prisma/prisma.service';
import { LoginDto } from './dto/login.dto';
import { RegisterDto } from './dto/register.dto';

type SafeAuthUser = Pick<User, 'id' | 'email' | 'name'>;

@Injectable()
export class AuthService {
  constructor(
    private readonly jwtService: JwtService,
    private readonly prisma: PrismaService,
  ) {}

  async register(registerDto: RegisterDto) {
    const normalizedEmail = this.normalizeEmail(registerDto.email);
    const existingUser = await this.prisma.user.findUnique({
      where: { email: normalizedEmail },
    });

    if (existingUser) {
      throw new ConflictException('User with this email already exists');
    }

    const passwordHash = await bcrypt.hash(registerDto.password, 10);
    const user = await this.prisma.user.create({
      data: {
        email: normalizedEmail,
        name: registerDto.name,
        passwordHash,
      },
    });

    return this.buildAuthResponse({
      email: normalizedEmail,
      id: user.id,
      name: user.name,
    });
  }

  async login(loginDto: LoginDto) {
    const normalizedEmail = this.normalizeEmail(loginDto.email);
    const user = await this.prisma.user.findUnique({
      where: { email: normalizedEmail },
    });

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

  async findById(userId: string) {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        email: true,
        name: true,
      },
    });

    return user ?? undefined;
  }

  private buildAuthResponse(user: Pick<User, 'id' | 'email' | 'name'>) {
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

  private toSafeUser(user: Pick<User, 'id' | 'email' | 'name'>): SafeAuthUser {
    return {
      id: user.id,
      email: user.email,
      name: user.name,
    };
  }

  private normalizeEmail(email: string) {
    return email.trim().toLowerCase();
  }
}
