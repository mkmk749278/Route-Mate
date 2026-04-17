import {
  BadRequestException,
  ConflictException,
  ForbiddenException,
  Injectable,
  NotFoundException,
} from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { CreateRouteInterestDto } from './dto/create-route-interest.dto';
import { OwnerDecisionRouteInterestDto } from './dto/owner-decision-route-interest.dto';

const ACTIVE_ROUTE_INTEREST_STATUSES = ['pending', 'accepted'] as const;

type RouteInterestWithRelations = {
  id: string;
  routePostId: string;
  requesterUserId: string;
  ownerUserId: string;
  status: string;
  createdAt: Date;
  updatedAt: Date;
  routePost: {
    id: string;
    origin: string;
    destination: string;
    travelDate: Date;
    preferredDepartureTime: string;
  };
  requester: {
    id: string;
    name: string;
    city: string | null;
    avatarUrl: string | null;
    phone: string | null;
  };
  owner: {
    id: string;
    name: string;
    city: string | null;
    avatarUrl: string | null;
    phone: string | null;
  };
};

@Injectable()
export class RouteInterestsService {
  constructor(private readonly prisma: PrismaService) {}

  async createRouteInterest(
    requesterUserId: string,
    createRouteInterestDto: CreateRouteInterestDto,
  ) {
    const routePost = await this.prisma.routePost.findUnique({
      where: { id: createRouteInterestDto.routePostId },
      select: { id: true, userId: true },
    });

    if (!routePost) {
      throw new NotFoundException('Route post not found');
    }

    if (routePost.userId === requesterUserId) {
      throw new BadRequestException(
        'You cannot express interest in your own route post',
      );
    }

    const existingActiveInterest = await this.prisma.routeInterest.findFirst({
      where: {
        routePostId: routePost.id,
        requesterUserId,
        status: {
          in: [...ACTIVE_ROUTE_INTEREST_STATUSES],
        },
      },
      select: { id: true },
    });

    if (existingActiveInterest) {
      throw new ConflictException(
        'An active route interest already exists for this route',
      );
    }

    const routeInterest = await this.prisma.routeInterest.create({
      data: {
        routePostId: routePost.id,
        requesterUserId,
        ownerUserId: routePost.userId,
      },
      select: this.routeInterestSelect,
    });

    return { interest: this.toView(routeInterest, requesterUserId) };
  }

  async listIncoming(ownerUserId: string) {
    const interests = await this.prisma.routeInterest.findMany({
      where: { ownerUserId },
      orderBy: { createdAt: 'desc' },
      select: this.routeInterestSelect,
    });

    return {
      interests: interests.map((interest) =>
        this.toView(interest, ownerUserId),
      ),
    };
  }

  async listOutgoing(requesterUserId: string) {
    const interests = await this.prisma.routeInterest.findMany({
      where: { requesterUserId },
      orderBy: { createdAt: 'desc' },
      select: this.routeInterestSelect,
    });

    return {
      interests: interests.map((interest) =>
        this.toView(interest, requesterUserId),
      ),
    };
  }

  async ownerDecision(
    ownerUserId: string,
    routeInterestId: string,
    ownerDecisionRouteInterestDto: OwnerDecisionRouteInterestDto,
  ) {
    const routeInterest = await this.prisma.routeInterest.findUnique({
      where: { id: routeInterestId },
      select: this.routeInterestSelect,
    });

    if (!routeInterest) {
      throw new NotFoundException('Route interest not found');
    }

    if (routeInterest.ownerUserId !== ownerUserId) {
      throw new ForbiddenException(
        'Only the route owner can update this request',
      );
    }

    if (routeInterest.status !== 'pending') {
      throw new ConflictException(
        'Only pending route interests can be updated',
      );
    }

    const updated = await this.prisma.routeInterest.update({
      where: { id: routeInterestId },
      data: { status: ownerDecisionRouteInterestDto.status },
      select: this.routeInterestSelect,
    });

    return { interest: this.toView(updated, ownerUserId) };
  }

  private toView(interest: RouteInterestWithRelations, viewerUserId: string) {
    const isAccepted = interest.status === 'accepted';
    const showRequesterPhone =
      isAccepted && viewerUserId === interest.ownerUserId;
    const showOwnerPhone =
      isAccepted && viewerUserId === interest.requesterUserId;

    return {
      id: interest.id,
      routePostId: interest.routePostId,
      requesterUserId: interest.requesterUserId,
      ownerUserId: interest.ownerUserId,
      status: interest.status,
      createdAt: interest.createdAt,
      updatedAt: interest.updatedAt,
      route: interest.routePost,
      requester: {
        id: interest.requester.id,
        name: interest.requester.name,
        city: interest.requester.city,
        avatarUrl: interest.requester.avatarUrl,
        phone: showRequesterPhone ? interest.requester.phone : null,
      },
      owner: {
        id: interest.owner.id,
        name: interest.owner.name,
        city: interest.owner.city,
        avatarUrl: interest.owner.avatarUrl,
        phone: showOwnerPhone ? interest.owner.phone : null,
      },
    };
  }

  private get routeInterestSelect() {
    return {
      id: true,
      routePostId: true,
      requesterUserId: true,
      ownerUserId: true,
      status: true,
      createdAt: true,
      updatedAt: true,
      routePost: {
        select: {
          id: true,
          origin: true,
          destination: true,
          travelDate: true,
          preferredDepartureTime: true,
        },
      },
      requester: {
        select: {
          id: true,
          name: true,
          city: true,
          avatarUrl: true,
          phone: true,
        },
      },
      owner: {
        select: {
          id: true,
          name: true,
          city: true,
          avatarUrl: true,
          phone: true,
        },
      },
    } as const;
  }
}
