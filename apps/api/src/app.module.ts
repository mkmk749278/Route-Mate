import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import Joi from 'joi';
import { AuthModule } from './modules/auth/auth.module';
import { HealthModule } from './modules/health/health.module';
import { RouteInterestsModule } from './modules/route-interests/route-interests.module';
import { RoutesModule } from './modules/routes/routes.module';
import { UsersModule } from './modules/users/users.module';
import { PrismaModule } from './prisma/prisma.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      cache: true,
      envFilePath: ['.env.local', '.env'],
      validationSchema: Joi.object({
        APP_ENV: Joi.string()
          .valid('development', 'test', 'staging', 'production')
          .default('development'),
        API_PORT: Joi.number().port().default(3000),
        CORS_ORIGIN: Joi.string().allow('').default(''),
        DATABASE_URL: Joi.string().uri().required(),
        REDIS_URL: Joi.string().uri().optional(),
        JWT_SECRET: Joi.string().min(16).required(),
        JWT_EXPIRES_IN: Joi.string().default('7d'),
      }),
    }),
    PrismaModule,
    HealthModule,
    AuthModule,
    UsersModule,
    RoutesModule,
    RouteInterestsModule,
  ],
})
export class AppModule {}
