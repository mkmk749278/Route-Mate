import { Module } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import Joi from 'joi';
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
      }),
    }),
    HealthModule,
  ],
})
export class AppModule {}
