import { plainToInstance, Transform } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsInt,
  IsString,
  Matches,
  Max,
  Min,
  validateSync,
} from 'class-validator';
import { DEFAULT_DATABASE_URL, DEFAULT_SERVER_VERSION } from '../common/constants';

const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);

function toBoolean(value: unknown): boolean {
  if (typeof value === 'boolean') {
    return value;
  }

  if (typeof value === 'string') {
    return TRUE_VALUES.has(value.toLowerCase());
  }

  return false;
}

export class EnvVariables {
  @IsIn(['development', 'test', 'production'])
  NODE_ENV!: string;

  @Transform(({ value }) => Number(value))
  @IsInt()
  @Min(1)
  @Max(65535)
  PORT!: number;

  @Matches(/^\/[a-z0-9][a-z0-9/-]*$/)
  API_PREFIX!: string;

  @IsString()
  SERVER_VERSION!: string;

  @IsString()
  DATABASE_URL!: string;

  @IsString()
  REDIS_URL!: string;

  @IsString()
  JWT_ACCESS_SECRET!: string;

  @IsString()
  JWT_REFRESH_SECRET!: string;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  ENABLE_SWAGGER!: boolean;

  @IsIn(['debug', 'info', 'warn', 'error'])
  LOG_LEVEL!: string;
}

export function validateEnv(config: Record<string, unknown>): EnvVariables {
  const normalizedConfig = {
    NODE_ENV: config.NODE_ENV ?? 'development',
    PORT: config.PORT ?? 3000,
    API_PREFIX: config.API_PREFIX ?? '/api',
    SERVER_VERSION: config.SERVER_VERSION ?? DEFAULT_SERVER_VERSION,
    DATABASE_URL: config.DATABASE_URL ?? DEFAULT_DATABASE_URL,
    REDIS_URL: config.REDIS_URL ?? 'redis://redis:6379',
    JWT_ACCESS_SECRET: config.JWT_ACCESS_SECRET ?? 'change_me_access_secret',
    JWT_REFRESH_SECRET: config.JWT_REFRESH_SECRET ?? 'change_me_refresh_secret',
    ENABLE_SWAGGER: config.ENABLE_SWAGGER ?? true,
    LOG_LEVEL: config.LOG_LEVEL ?? 'debug',
  };

  const validatedConfig = plainToInstance(EnvVariables, normalizedConfig, {
    enableImplicitConversion: false,
  });

  const errors = validateSync(validatedConfig, {
    skipMissingProperties: false,
  });

  if (errors.length > 0) {
    throw new Error(errors.toString());
  }

  return validatedConfig;
}
