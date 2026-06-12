import { MiddlewareConsumer, Module, NestModule } from '@nestjs/common';
import { ConfigModule } from '@nestjs/config';
import { AuthModule } from './auth/auth.module';
import { RequestIdMiddleware } from './common/request-id.middleware';
import { ContactsModule } from './contacts/contacts.module';
import { CryptoModule } from './crypto/crypto.module';
import { DatabaseModule } from './database/database.module';
import { DevicesModule } from './devices/devices.module';
import { validateEnv } from './config/env.validation';
import { HealthModule } from './health/health.module';
import { KeysModule } from './keys/keys.module';
import { LiveModule } from './live/live.module';
import { ObjectsModule } from './objects/objects.module';
import { UsersModule } from './users/users.module';
import { VersionModule } from './version/version.module';

@Module({
  imports: [
    ConfigModule.forRoot({
      isGlobal: true,
      validate: validateEnv,
    }),
    DatabaseModule,
    HealthModule,
    VersionModule,
    AuthModule,
    UsersModule,
    DevicesModule,
    KeysModule,
    ContactsModule,
    ObjectsModule,
    LiveModule,
    CryptoModule,
  ],
})
export class AppModule implements NestModule {
  configure(consumer: MiddlewareConsumer): void {
    consumer.apply(RequestIdMiddleware).forRoutes('*');
  }
}
