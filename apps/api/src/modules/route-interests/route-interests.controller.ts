import {
  Body,
  Controller,
  Get,
  Param,
  Patch,
  Post,
  Req,
  UseGuards,
} from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { CreateRouteInterestDto } from './dto/create-route-interest.dto';
import { OwnerDecisionRouteInterestDto } from './dto/owner-decision-route-interest.dto';
import { RouteInterestsService } from './route-interests.service';

type RequestUser = {
  id: string;
};

@Controller('route-interests')
@UseGuards(JwtAuthGuard)
export class RouteInterestsController {
  constructor(private readonly routeInterestsService: RouteInterestsService) {}

  @Post()
  createRouteInterest(
    @Req() request: Request,
    @Body() createRouteInterestDto: CreateRouteInterestDto,
  ) {
    const user = request.user as RequestUser;
    return this.routeInterestsService.createRouteInterest(
      user.id,
      createRouteInterestDto,
    );
  }

  @Get('incoming')
  listIncoming(@Req() request: Request) {
    const user = request.user as RequestUser;
    return this.routeInterestsService.listIncoming(user.id);
  }

  @Get('outgoing')
  listOutgoing(@Req() request: Request) {
    const user = request.user as RequestUser;
    return this.routeInterestsService.listOutgoing(user.id);
  }

  @Patch(':routeInterestId/owner-decision')
  ownerDecision(
    @Req() request: Request,
    @Param('routeInterestId') routeInterestId: string,
    @Body() ownerDecisionRouteInterestDto: OwnerDecisionRouteInterestDto,
  ) {
    const user = request.user as RequestUser;
    return this.routeInterestsService.ownerDecision(
      user.id,
      routeInterestId,
      ownerDecisionRouteInterestDto,
    );
  }
}
