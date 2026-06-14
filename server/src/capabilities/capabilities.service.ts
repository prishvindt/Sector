import { Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import {
  CryptoProfile,
  DataResidency,
  DeploymentMode,
  ServerCapabilitiesResponse,
} from './capabilities.types';

const DEFAULT_WARNINGS = ['backend skeleton does not implement auth, contacts or relay yet'];

@Injectable()
export class CapabilitiesService {
  constructor(private readonly configService: ConfigService) {}

  getCapabilities(): ServerCapabilitiesResponse {
    // Public declarative policy only. Do not expose secrets, internal URLs, or credentials here.
    return {
      serverName: this.configService.get<string>('SERVER_NAME') ?? 'Sector self-hosted',
      operatorName: this.configService.get<string>('OPERATOR_NAME') ?? 'Private operator',
      deploymentMode:
        this.configService.get<DeploymentMode>('DEPLOYMENT_MODE') ?? 'private_self_hosted',
      dataResidency: this.configService.get<DataResidency>('DATA_RESIDENCY') ?? 'unknown',
      cryptoProfile: this.configService.get<CryptoProfile>('CRYPTO_PROFILE') ?? 'production_e2e',
      relayOnly: this.configService.get<boolean>('RELAY_ONLY') ?? true,
      storesUserArchive: this.configService.get<boolean>('STORES_USER_ARCHIVE') ?? false,
      payloadTtlSeconds: this.configService.get<number>('PAYLOAD_TTL_SECONDS') ?? 604800,
      mediaTtlSeconds: this.configService.get<number>('MEDIA_TTL_SECONDS') ?? 604800,
      deleteAfterDeliverySupported:
        this.configService.get<boolean>('DELETE_AFTER_DELIVERY_SUPPORTED') ?? true,
      features: {
        registration: this.configService.get<boolean>('FEATURE_REGISTRATION') ?? false,
        emailVerification:
          this.configService.get<boolean>('FEATURE_EMAIL_VERIFICATION') ?? false,
        contacts: this.configService.get<boolean>('FEATURE_CONTACTS') ?? false,
        encryptedObjects: this.configService.get<boolean>('FEATURE_ENCRYPTED_OBJECTS') ?? false,
        encryptedMedia: this.configService.get<boolean>('FEATURE_ENCRYPTED_MEDIA') ?? false,
        liveLocation: this.configService.get<boolean>('FEATURE_LIVE_LOCATION') ?? false,
        cloudBackup: this.configService.get<boolean>('FEATURE_CLOUD_BACKUP') ?? false,
        webMap: this.configService.get<boolean>('FEATURE_WEB_MAP') ?? false,
      },
      warnings: DEFAULT_WARNINGS,
    };
  }
}
