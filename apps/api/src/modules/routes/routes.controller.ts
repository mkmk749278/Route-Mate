import {
  Body,
  Controller,
  Get,
  Post,
  Query,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { CreateRouteDto } from './dto/create-route.dto';
import { DiscoverRoutesQueryDto } from './dto/discover-routes-query.dto';
import { RoutesService } from './routes.service';

type RequestUser = {
  id: string;
};

@Controller('routes')
@UseGuards(JwtAuthGuard)
export class RoutesController {
  constructor(private readonly routesService: RoutesService) {}

  @Post()
  createRoute(@Req() request: Request, @Body() createRouteDto: CreateRouteDto) {
    const user = request.user as RequestUser;
    return this.routesService.createRoute(user.id, createRouteDto);
  }

  @Get('me')
  listMyRoutes(@Req() request: Request) {
    const user = request.user as RequestUser;
    return this.routesService.listMyRoutes(user.id);
  }

  @Get('discover')
  discoverRoutes(
    @Req() request: Request,
    @Query() discoverRoutesQueryDto: DiscoverRoutesQueryDto,
  ) {
    const user = request.user as RequestUser;
    return this.routesService.discoverRoutes(user.id, discoverRoutesQueryDto);
  }
}
