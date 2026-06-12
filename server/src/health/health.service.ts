import { Injectable } from '@nestjs/common';
import { SERVICE_NAME } from '../common/constants';

type DependencyStatus = 'ok' | 'not_checked';

export type HealthResponse = {
  readonly status: 'ok';
  readonly service: string;
  readonly timestamp: string;
  readonly database: DependencyStatus;
  readonly redis: DependencyStatus;
};

@Injectable()
export class HealthService {
  getHealth(): HealthResponse {
    return {
      status: 'ok',
      service: SERVICE_NAME,
      timestamp: new Date().toISOString(),
      database: 'not_checked',
      redis: 'not_checked',
    };
  }
}
