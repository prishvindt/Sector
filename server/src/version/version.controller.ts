import { Controller, Get } from '@nestjs/common';
import { VersionResponse, VersionService } from './version.service';

@Controller('version')
export class VersionController {
  constructor(private readonly versionService: VersionService) {}

  @Get()
  getVersion(): VersionResponse {
    return this.versionService.getVersion();
  }
}
