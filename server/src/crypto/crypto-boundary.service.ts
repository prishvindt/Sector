import { Injectable } from '@nestjs/common';

export type CryptoBoundaryRule = {
  readonly name: string;
  readonly description: string;
};

@Injectable()
export class CryptoBoundaryService {
  /**
   * Documents the server-side crypto boundary for future modules.
   * The backend accepts encrypted payloads as opaque blobs and never
   * owns enough material to decrypt user data.
   */
  describeBoundary(): CryptoBoundaryRule[] {
    return [
      {
        name: 'no_server_decryption',
        description: 'The server must not decrypt encrypted payload contents.',
      },
      {
        name: 'no_private_keys',
        description: 'The server must not store user private keys.',
      },
      {
        name: 'opaque_payloads',
        description: 'The backend stores encrypted payloads only as opaque blobs.',
      },
      {
        name: 'client_side_crypto',
        description: 'Real cryptography belongs to clients and a separate design task.',
      },
      {
        name: 'metadata_only_validation',
        description: 'The server may validate envelope metadata, not plaintext contents.',
      },
    ];
  }
}
