import { Injectable } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { UpdateMeDto } from './dto/update-me.dto';

@Injectable()
export class UsersService {
  constructor(private readonly prisma: PrismaService) {}

  async getMe(userId: string) {
    const user = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        id: true,
        email: true,
        name: true,
        phone: true,
        city: true,
        gender: true,
        bio: true,
        avatarUrl: true,
        isProfileComplete: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    return { user };
  }

  async updateMe(userId: string, updateMeDto: UpdateMeDto) {
    const profileFields = await this.prisma.user.findUnique({
      where: { id: userId },
      select: {
        name: true,
        city: true,
        bio: true,
      },
    });

    const mergedForCompletion = {
      name: updateMeDto.name ?? profileFields?.name ?? '',
      city: updateMeDto.city ?? profileFields?.city ?? '',
      bio: updateMeDto.bio ?? profileFields?.bio ?? '',
    };

    const isProfileComplete = Boolean(
      mergedForCompletion.name &&
      mergedForCompletion.city &&
      mergedForCompletion.bio,
    );

    const user = await this.prisma.user.update({
      where: { id: userId },
      data: {
        ...updateMeDto,
        isProfileComplete,
      },
      select: {
        id: true,
        email: true,
        name: true,
        phone: true,
        city: true,
        gender: true,
        bio: true,
        avatarUrl: true,
        isProfileComplete: true,
        createdAt: true,
        updatedAt: true,
      },
    });

    return { user };
  }
}
