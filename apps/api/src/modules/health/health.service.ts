import { Injectable } from '@nestjs/common';

@Injectable()
export class HealthService {
  getStatus() {
    return {
      status: 'healthy',
      service: 'route-mates-api',
      timestamp: new Date().toISOString(),
    };
  }
}
