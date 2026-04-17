import { Injectable } from '@nestjs/common';
import { Prisma } from '@prisma/client';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateRouteDto } from './dto/create-route.dto';
import { DiscoverRoutesQueryDto } from './dto/discover-routes-query.dto';

const DEFAULT_DISCOVER_LIMIT = 20;

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

  async discoverRoutes(userId: string, query: DiscoverRoutesQueryDto) {
    const limit = query.limit ?? DEFAULT_DISCOVER_LIMIT;
    const offset = query.offset ?? 0;
    const where: Prisma.RoutePostWhereInput = {
      userId: { not: userId },
    };

    if (query.origin) {
      const origin = query.origin.trim();
      if (origin) {
        where.origin = { contains: origin, mode: 'insensitive' };
      }
    }

    if (query.destination) {
      const destination = query.destination.trim();
      if (destination) {
        where.destination = {
          contains: destination,
          mode: 'insensitive',
        };
      }
    }

    if (query.travelDate) {
      const travelDate = new Date(query.travelDate);
      const startOfDayUtc = new Date(
        Date.UTC(
          travelDate.getUTCFullYear(),
          travelDate.getUTCMonth(),
          travelDate.getUTCDate(),
        ),
      );
      const endOfDayUtc = new Date(startOfDayUtc);
      endOfDayUtc.setUTCDate(endOfDayUtc.getUTCDate() + 1);
      where.travelDate = { gte: startOfDayUtc, lt: endOfDayUtc };
    }

    const routes = await this.prisma.routePost.findMany({
      where,
      take: limit,
      skip: offset,
      orderBy: [{ travelDate: 'asc' }, { createdAt: 'desc' }],
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
        user: {
          select: {
            id: true,
            name: true,
            city: true,
            avatarUrl: true,
          },
        },
      },
    });

    return {
      routes: routes.map(({ user, ...route }) => ({
        ...route,
        owner: user,
      })),
      pagination: {
        limit,
        offset,
      },
    };
  }
}
