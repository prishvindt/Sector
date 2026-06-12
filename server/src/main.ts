import { Logger, ValidationPipe } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { NestFactory } from '@nestjs/core';
import { AppModule } from './app.module';

function normalizeGlobalPrefix(prefix: string): string {
  return prefix.replace(/^\/+|\/+$/g, '');
}

async function bootstrap(): Promise<void> {
  const app = await NestFactory.create(AppModule, {
    bufferLogs: true,
  });

  const configService = app.get(ConfigService);
  const apiPrefix = normalizeGlobalPrefix(configService.get<string>('API_PREFIX') ?? '/api');

  if (apiPrefix.length > 0) {
    app.setGlobalPrefix(apiPrefix);
  }

  app.useGlobalPipes(
    new ValidationPipe({
      transform: true,
      whitelist: true,
    }),
  );

  const port = configService.get<number>('PORT') ?? 3000;
  await app.listen(port);

  Logger.log(`sector-backend listening on port ${port}`, 'Bootstrap');
}

void bootstrap();
