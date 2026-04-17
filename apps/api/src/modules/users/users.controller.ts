import { Body, Controller, Get, Patch, Req, UseGuards } from '@nestjs/common';
import type { Request } from 'express';
import { JwtAuthGuard } from '../auth/guards/jwt-auth.guard';
import { UpdateMeDto } from './dto/update-me.dto';
import { UsersService } from './users.service';

type RequestUser = {
  id: string;
};

@Controller('users')
@UseGuards(JwtAuthGuard)
export class UsersController {
  constructor(private readonly usersService: UsersService) {}

  @Get('me')
  getMe(@Req() request: Request) {
    const user = request.user as RequestUser;
    return this.usersService.getMe(user.id);
  }

  @Patch('me')
  updateMe(@Req() request: Request, @Body() updateMeDto: UpdateMeDto) {
    const user = request.user as RequestUser;
    return this.usersService.updateMe(user.id, updateMeDto);
  }
}
