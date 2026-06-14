# Server Capabilities Contract

## Purpose

`GET /api/server/capabilities` lets a Sector client learn what kind of server it is connected to before sending sensitive payload. The response is a public declaration of server policy, deployment mode, crypto profile, relay-only behavior, TTL policy, delete-after-delivery support, and enabled feature flags.

Capabilities are not proof that the server is trustworthy. A client must still show warnings, use HTTPS, and verify certificate or fingerprint state when that is implemented.

## Endpoint

```text
GET /api/server/capabilities
```

The Nest controller path is `GET /server/capabilities`; with the global `API_PREFIX=/api` the public path is `GET /api/server/capabilities`.

The endpoint is public and must not require authentication.

## Example Response

```json
{
  "serverName": "Sector self-hosted",
  "operatorName": "Private operator",
  "deploymentMode": "private_self_hosted",
  "dataResidency": "unknown",
  "cryptoProfile": "production_e2e",
  "relayOnly": true,
  "storesUserArchive": false,
  "payloadTtlSeconds": 604800,
  "mediaTtlSeconds": 604800,
  "deleteAfterDeliverySupported": true,
  "features": {
    "registration": false,
    "emailVerification": false,
    "contacts": false,
    "encryptedObjects": false,
    "encryptedMedia": false,
    "liveLocation": false,
    "cloudBackup": false,
    "webMap": false
  },
  "warnings": [
    "backend skeleton does not implement auth, contacts or relay yet"
  ]
}
```

## Fields

- `serverName`: public display name for the server. It must not be empty.
- `operatorName`: public operator label. It may be an empty string, but clients should warn when it is empty or unknown.
- `deploymentMode`: declared deployment model.
- `dataResidency`: declared data residency model.
- `cryptoProfile`: declared crypto profile.
- `relayOnly`: whether the server claims to avoid permanent user archive storage and behave as a relay for private payload.
- `storesUserArchive`: whether the server claims to store a persistent user archive.
- `payloadTtlSeconds`: TTL for encrypted payload queues, in seconds. `0` means no positive TTL is declared.
- `mediaTtlSeconds`: TTL for encrypted media blobs, in seconds. `0` means no positive TTL is declared.
- `deleteAfterDeliverySupported`: whether server policy supports deleting relay payload after delivery.
- `features.registration`: whether registration is enabled.
- `features.emailVerification`: whether the server supports the email verification capability. It does not mean email verification is mandatory for all accounts.
- `features.contacts`: whether contacts are enabled.
- `features.encryptedObjects`: whether encrypted object sync is enabled.
- `features.encryptedMedia`: whether encrypted media sync is enabled.
- `features.liveLocation`: whether live location is enabled.
- `features.cloudBackup`: whether server-side user cloud backup is enabled.
- `features.webMap`: whether a web map surface is enabled.
- `warnings`: public human-readable warnings. These must not contain secrets or internal credentials.

## Future Feature Flags

The current backend skeleton does not implement auth, account registration, device linking, recovery, or email-based accounts. Future contract versions should declare these capabilities explicitly instead of making clients infer behavior from a generic `registration` flag.

Future identity and recovery flags:

- `features.emailLogin`: whether email-based login is enabled.
- `features.emailVerificationRequired`: whether email verification is mandatory for email-based accounts when email-based accounts are enabled.
- `features.noEmailAccounts`: whether account registration can work without email.
- `features.inviteRegistration`: whether registration can be restricted to invite codes.
- `features.deviceRecovery`: whether the server supports account/device recovery flows.
- `features.recoveryPhraseRequired`: whether a recovery phrase or recovery key is required for safe account transfer.
- `features.accountRecovery`: whether account recovery is available at all.
- `features.publicKeyRegistration`: whether devices can register public keys.
- `features.fingerprintVerification`: whether the server/client flow supports explicit key fingerprint confirmation.

These fields are a future additive extension. Until implemented by `server/src`, clients must treat missing fields as unsupported or unknown and must not assume email is required by default.

`features.emailVerification` is part of the current response shape and only declares support for email verification. `features.emailVerificationRequired` is the optional future flag for mandatory verification of email-based accounts. No-email accounts must not require email verification.

## Enum Values

`deploymentMode` supports only:

- `local_only`
- `dev_test`
- `rf_production`
- `international_production`
- `private_self_hosted`
- `regulated_self_hosted`
- `relay_only_server`

`dataResidency` supports only:

- `unknown`
- `ru`
- `international`
- `regulated`

`cryptoProfile` supports only:

- `dev_local_noop`
- `production_e2e`
- `regulated_crypto_provider`

Unknown enum values are invalid configuration. The server must fail during startup validation instead of returning an ambiguous capabilities response.

## Client Behavior

The client should fetch capabilities before sending coordinates, notes, live location, media, encrypted objects, or other sensitive payload to a custom or self-hosted server.

The client must block sending coordinates, notes, live location, and media when:

- `cryptoProfile = dev_local_noop`;
- `deploymentMode = dev_test`;
- the server does not support a feature required for the attempted action;
- `relayOnly=false` and `storesUserArchive=true` while the user expected relay-only behavior;
- certificate or fingerprint state changed, after certificate/fingerprint verification is implemented;
- `dataResidency` does not match the selected privacy or compliance mode.

The client should show a warning when:

- the server is custom or self-hosted;
- `dataResidency=unknown`;
- `dataResidency=international`;
- `operatorName` is unknown or looks empty;
- `features.liveLocation=true`;
- `features.cloudBackup=true`;
- `features.webMap=true` for private data.

Account identity behavior:

- the client must not require email when `features.noEmailAccounts=true`;
- if a server requires email, the client should warn that email is additional personal data and may be undesirable for regulated or private self-hosted use;
- if `features.emailVerification=true`, the server supports an email verification capability, but no-email accounts must not require email verification;
- if `features.emailVerificationRequired=true`, email verification is mandatory only for email-based accounts;
- if the server does not support `features.deviceRecovery` or `features.recoveryPhraseRequired`, the client should warn about the risk of losing access when changing phones;
- if the server supports only email login, regulated/private modes may reject or discourage that server profile;
- trusted contacts must never be established only from email; fingerprint confirmation is required.

Capabilities can help choose client behavior, but they do not replace user consent, TLS validation, certificate/fingerprint checks, or end-to-end encryption.

## Security Warnings

This endpoint must not reveal secrets. It must never return JWT secrets, database URLs, Redis URLs, internal passwords, private keys, private service endpoints, or credentials.

The endpoint is public by design. Its content must be limited to non-secret deployment policy and feature declarations.

If `cryptoProfile = dev_local_noop`, a future client must prevent real sensitive payload from being sent to that server.

## Relation To Relay-Only Model

`relayOnly=true` declares that private encrypted payload is expected to be temporary delivery data, not a permanent user archive. In relay-only mode:

- encrypted payload should have a TTL;
- encrypted media should have a TTL;
- delete-after-delivery should be supported when possible;
- server backups should cover service data and configuration, not permanent user archives;
- private keys and plaintext payload must never be stored by the server.

`storesUserArchive=true` means the server claims to keep a persistent user archive. Clients should treat this as higher risk for sensitive payload and ask for explicit user consent.

## Versioning And Future Compatibility

The initial contract is additive. Clients should ignore unknown response fields and must not assume that new fields are secrets or feature support.

Servers must keep the existing field names and enum values stable. Breaking changes require a future explicit contract version field.

Feature flags only indicate declared support. A feature flag set to `true` does not define the full API for that feature and does not prove that the implementation is secure.
