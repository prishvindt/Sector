import { Module } from '@nestjs/common';
import { CryptoBoundaryService } from './crypto-boundary.service';

@Module({
  providers: [CryptoBoundaryService],
  exports: [CryptoBoundaryService],
})
export class CryptoModule {}
