import { Injectable, OnModuleDestroy } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { PrismaClient } from '@prisma/client';
import { DEFAULT_DATABASE_URL } from '../common/constants';

@Injectable()
export class PrismaService extends PrismaClient implements OnModuleDestroy {
  constructor(configService: ConfigService) {
    super({
      datasources: {
        db: {
          url: configService.get<string>('DATABASE_URL') ?? DEFAULT_DATABASE_URL,
        },
      },
    });
  }

  async onModuleDestroy(): Promise<void> {
    await this.$disconnect();
  }
}
