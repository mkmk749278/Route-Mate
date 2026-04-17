import { Module } from '@nestjs/common';
import { RouteInterestsController } from './route-interests.controller';
import { RouteInterestsService } from './route-interests.service';

@Module({
  controllers: [RouteInterestsController],
  providers: [RouteInterestsService],
})
export class RouteInterestsModule {}
