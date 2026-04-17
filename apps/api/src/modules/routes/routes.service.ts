import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateRouteDto } from './dto/create-route.dto';

@Injectable()
export class RoutesService {
  constructor(private readonly prisma: PrismaService) {}

  async createRoute(userId: string, createRouteDto: CreateRouteDto) {
    const route = await this.prisma.routePost.create({
      data: {
        userId,
        origin: createRouteDto.origin,
        destination: createRouteDto.destination,
        travelDate: new Date(createRouteDto.travelDate),
        preferredDepartureTime: createRouteDto.preferredDepartureTime,
        seatCount: createRouteDto.seatCount,
        notes: createRouteDto.notes,
      },
      select: {
        id: true,
        userId: true,
        origin: true,
        destination: true,
        travelDate: true,
        preferredDepartureTime: true,
        seatCount: true,
        notes: true,
        status: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    return { route };
  }

  async listMyRoutes(userId: string) {
    const routes = await this.prisma.routePost.findMany({
      where: { userId },
      orderBy: { createdAt: 'desc' },
      select: {
        id: true,
        userId: true,
        origin: true,
        destination: true,
        travelDate: true,
        preferredDepartureTime: true,
        seatCount: true,
        notes: true,
        status: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    return { routes };
  }
}
