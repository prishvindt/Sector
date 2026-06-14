import { Controller, Get } from '@nestjs/common';
import { CapabilitiesService } from './capabilities.service';
import { ServerCapabilitiesResponse } from './capabilities.types';

@Controller('server')
export class CapabilitiesController {
  constructor(private readonly capabilitiesService: CapabilitiesService) {}

  @Get('capabilities')
  getCapabilities(): ServerCapabilitiesResponse {
    return this.capabilitiesService.getCapabilities();
  }
}
