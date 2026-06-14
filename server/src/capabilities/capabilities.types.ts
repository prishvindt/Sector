export const DEPLOYMENT_MODES = [
  'local_only',
  'dev_test',
  'rf_production',
  'international_production',
  'private_self_hosted',
  'regulated_self_hosted',
  'relay_only_server',
] as const;

export type DeploymentMode = (typeof DEPLOYMENT_MODES)[number];

export const DATA_RESIDENCIES = ['unknown', 'ru', 'international', 'regulated'] as const;

export type DataResidency = (typeof DATA_RESIDENCIES)[number];

export const CRYPTO_PROFILES = [
  'dev_local_noop',
  'production_e2e',
  'regulated_crypto_provider',
] as const;

export type CryptoProfile = (typeof CRYPTO_PROFILES)[number];

export type ServerCapabilitiesFeatures = {
  readonly registration: boolean;
  readonly emailVerification: boolean;
  readonly contacts: boolean;
  readonly encryptedObjects: boolean;
  readonly encryptedMedia: boolean;
  readonly liveLocation: boolean;
  readonly cloudBackup: boolean;
  readonly webMap: boolean;
};

export type ServerCapabilitiesResponse = {
  readonly serverName: string;
  readonly operatorName: string;
  readonly deploymentMode: DeploymentMode;
  readonly dataResidency: DataResidency;
  readonly cryptoProfile: CryptoProfile;
  readonly relayOnly: boolean;
  readonly storesUserArchive: boolean;
  readonly payloadTtlSeconds: number;
  readonly mediaTtlSeconds: number;
  readonly deleteAfterDeliverySupported: boolean;
  readonly features: ServerCapabilitiesFeatures;
  readonly warnings: readonly string[];
};
