import { Injectable, NestMiddleware } from '@nestjs/common';
import { randomUUID } from 'node:crypto';
import { NextFunction, Request, Response } from 'express';

type RequestWithId = Request & {
  requestId?: string;
};

@Injectable()
export class RequestIdMiddleware implements NestMiddleware {
  use(req: RequestWithId, res: Response, next: NextFunction): void {
    const headerValue = req.header('x-request-id');
    const requestId = headerValue && headerValue.length > 0 ? headerValue : randomUUID();

    req.requestId = requestId;
    res.setHeader('x-request-id', requestId);

    next();
  }
}
