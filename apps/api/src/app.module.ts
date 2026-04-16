import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import Joi from 'joi';
import { AuthModule } from './modules/auth/auth.module';
import { HealthModule } from './modules/health/health.module';

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
        JWT_SECRET: Joi.string()
          .min(16)
          .default('replace_with_a_secure_secret'),
        JWT_EXPIRES_IN: Joi.string().default('7d'),
      }),
    }),
    HealthModule,
    AuthModule,
  ],
})
export class AppModule {}
