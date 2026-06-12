import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { DEFAULT_SERVER_VERSION, SERVICE_NAME } from '../common/constants';

export type VersionResponse = {
  readonly service: string;
  readonly version: string;
  readonly nodeEnv: string;
};

@Injectable()
export class VersionService {
  constructor(private readonly configService: ConfigService) {}

  getVersion(): VersionResponse {
    return {
      service: SERVICE_NAME,
      version: this.configService.get<string>('SERVER_VERSION') ?? DEFAULT_SERVER_VERSION,
      nodeEnv: this.configService.get<string>('NODE_ENV') ?? 'development',
    };
  }
}
