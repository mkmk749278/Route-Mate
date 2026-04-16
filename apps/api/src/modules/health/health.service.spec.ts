import { HealthService } from './health.service';

describe('HealthService', () => {
  it('returns healthy status payload', () => {
    const service = new HealthService();
    const result = service.getStatus();

    expect(result.status).toBe('healthy');
    expect(result.service).toBe('route-mates-api');
    expect(result.timestamp).toBeTruthy();
  });
});
