import { plainToInstance, Transform } from 'class-transformer';
import {
  IsBoolean,
  IsIn,
  IsInt,
  IsNotEmpty,
  IsString,
  Matches,
  Max,
  Min,
  validateSync,
} from 'class-validator';
import {
  CRYPTO_PROFILES,
  DATA_RESIDENCIES,
  DEPLOYMENT_MODES,
  CryptoProfile,
  DataResidency,
  DeploymentMode,
} from '../capabilities/capabilities.types';
import { DEFAULT_DATABASE_URL, DEFAULT_SERVER_VERSION } from '../common/constants';

const TRUE_VALUES = new Set(['1', 'true', 'yes', 'on']);
const FALSE_VALUES = new Set(['0', 'false', 'no', 'off']);

function toBoolean(value: unknown): boolean | unknown {
  if (typeof value === 'boolean') {
    return value;
  }

  if (typeof value === 'string') {
    const normalizedValue = value.trim().toLowerCase();

    if (TRUE_VALUES.has(normalizedValue)) {
      return true;
    }

    if (FALSE_VALUES.has(normalizedValue)) {
      return false;
    }
  }

  return value;
}

function toInteger(value: unknown): number | unknown {
  if (typeof value === 'number') {
    return value;
  }

  if (typeof value === 'string' && value.trim().length > 0) {
    return Number(value);
  }

  return value;
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

  @IsString()
  @IsNotEmpty()
  @Matches(/\S/)
  SERVER_NAME!: string;

  @IsString()
  OPERATOR_NAME!: string;

  @IsIn(DEPLOYMENT_MODES)
  DEPLOYMENT_MODE!: DeploymentMode;

  @IsIn(DATA_RESIDENCIES)
  DATA_RESIDENCY!: DataResidency;

  @IsIn(CRYPTO_PROFILES)
  CRYPTO_PROFILE!: CryptoProfile;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  RELAY_ONLY!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  STORES_USER_ARCHIVE!: boolean;

  @Transform(({ value }) => toInteger(value))
  @IsInt()
  @Min(0)
  PAYLOAD_TTL_SECONDS!: number;

  @Transform(({ value }) => toInteger(value))
  @IsInt()
  @Min(0)
  MEDIA_TTL_SECONDS!: number;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  DELETE_AFTER_DELIVERY_SUPPORTED!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_REGISTRATION!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_EMAIL_VERIFICATION!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_CONTACTS!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_ENCRYPTED_OBJECTS!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_ENCRYPTED_MEDIA!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_LIVE_LOCATION!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_CLOUD_BACKUP!: boolean;

  @Transform(({ value }) => toBoolean(value))
  @IsBoolean()
  FEATURE_WEB_MAP!: boolean;
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
    SERVER_NAME: config.SERVER_NAME ?? 'Sector self-hosted',
    OPERATOR_NAME: config.OPERATOR_NAME ?? 'Private operator',
    DEPLOYMENT_MODE: config.DEPLOYMENT_MODE ?? 'private_self_hosted',
    DATA_RESIDENCY: config.DATA_RESIDENCY ?? 'unknown',
    CRYPTO_PROFILE: config.CRYPTO_PROFILE ?? 'production_e2e',
    RELAY_ONLY: config.RELAY_ONLY ?? true,
    STORES_USER_ARCHIVE: config.STORES_USER_ARCHIVE ?? false,
    PAYLOAD_TTL_SECONDS: config.PAYLOAD_TTL_SECONDS ?? 604800,
    MEDIA_TTL_SECONDS: config.MEDIA_TTL_SECONDS ?? 604800,
    DELETE_AFTER_DELIVERY_SUPPORTED: config.DELETE_AFTER_DELIVERY_SUPPORTED ?? true,
    FEATURE_REGISTRATION: config.FEATURE_REGISTRATION ?? false,
    FEATURE_EMAIL_VERIFICATION: config.FEATURE_EMAIL_VERIFICATION ?? false,
    FEATURE_CONTACTS: config.FEATURE_CONTACTS ?? false,
    FEATURE_ENCRYPTED_OBJECTS: config.FEATURE_ENCRYPTED_OBJECTS ?? false,
    FEATURE_ENCRYPTED_MEDIA: config.FEATURE_ENCRYPTED_MEDIA ?? false,
    FEATURE_LIVE_LOCATION: config.FEATURE_LIVE_LOCATION ?? false,
    FEATURE_CLOUD_BACKUP: config.FEATURE_CLOUD_BACKUP ?? false,
    FEATURE_WEB_MAP: config.FEATURE_WEB_MAP ?? false,
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
