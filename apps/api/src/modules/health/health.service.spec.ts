import { ServiceUnavailableException } from '@nestjs/common';
import { PrismaService } from '../../prisma/prisma.service';
import { HealthService } from './health.service';

describe('HealthService', () => {
  it('returns healthy status payload when database is reachable', async () => {
    const prisma = {
      $queryRaw: jest.fn().mockResolvedValue([{ '?column?': 1 }]),
    } as unknown as PrismaService;
    const service = new HealthService(prisma);
    const result = await service.getStatus();

    expect(result.status).toBe('healthy');
    expect(result.service).toBe('route-mates-api');
    expect(result.checks.database).toBe('up');
    expect(result.timestamp).toBeTruthy();
  });

  it('throws ServiceUnavailableException when database is unreachable', async () => {
    const prisma = {
      $queryRaw: jest.fn().mockRejectedValue(new Error('connection error')),
    } as unknown as PrismaService;
    const service = new HealthService(prisma);

    await expect(service.getStatus()).rejects.toBeInstanceOf(
      ServiceUnavailableException,
    );
  });
});
