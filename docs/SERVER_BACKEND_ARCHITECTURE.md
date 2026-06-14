# Серверная архитектура Sector

Этот документ описывает будущий сервер Sector. Требования к E2E, encrypted payload, live location и media sync связаны с [SECURITY_AND_SYNC_ARCHITECTURE.md](SECURITY_AND_SYNC_ARCHITECTURE.md), а deployment/server modes и client server profiles - с [CRYPTO_SECURITY_PROFILE.md](CRYPTO_SECURITY_PROFILE.md). Серверная часть пока не реализуется в коде приложения в рамках этой документационной задачи.

## 1. Назначение сервера

Сервер нужен как транспортный и учетный слой поверх local-first Android-приложения. Он не заменяет локальную Room-модель `sector_objects` и не должен делать базовую работу приложения зависимой от интернета.

Сервер:

- создает accounts без обязательного email;
- регистрирует устройства;
- хранит публичные ключи;
- хранит key fingerprints;
- связывает контакты;
- принимает encrypted objects;
- доставляет encrypted objects получателям;
- поддерживает live location sessions;
- позже поддерживает web sector-map.

Сервер не должен:

- читать payload;
- расшифровывать координаты;
- знать текст заметок;
- знать содержимое азимутных лучей;
- требовать email как базовый идентификатор;
- хранить приватные ключи.

В базовой модели приватных данных сервер работает как encrypted relay, а не как постоянный cloud archive пользовательского payload.

## relay-only backend mode

Сервер должен поддерживать режим, где нет постоянного архива пользовательских объектов. В этом режиме `encrypted_objects` и encrypted media рассматриваются как временная delivery queue, а не как бессрочное хранилище пользовательской истории.

Требования relay-only режима:

- нет постоянного архива пользовательских объектов;
- encrypted objects хранятся только во временной delivery queue;
- encrypted media blobs хранятся только до доставки или `expires_at`;
- сервер поддерживает TTL cleanup worker;
- сервер поддерживает delete-after-delivery;
- сервер не делает cloud backup пользовательского payload;
- backups касаются только служебной базы, конфигурации и audit logs, но не постоянного пользовательского архива;
- если encrypted payload временно находится в backup, backup retention должен быть минимальным и описан отдельно.

Сервер может хранить минимальные служебные данные: `account_id`, `device_id`, public keys, fingerprints, contact relations, refresh token hashes, delivery metadata и security logs. Эти данные должны быть отделены от пользовательского payload и иметь собственные retention/security policies.

## 2. Базовый стек

Рекомендованный стек без обязательного выбора:

- backend: NestJS или Ktor;
- database: PostgreSQL;
- Redis: sessions, rate limit, websocket state;
- reverse proxy: Nginx или Caddy;
- Docker Compose для локального и self-hosted запуска;
- migrations для схемы БД;
- structured logs.

Для быстрого старта предпочтителен NestJS + PostgreSQL: это даст быстрый REST/WebSocket skeleton, миграции и удобную админскую разработку. Ktor тоже допустим, если нужен Kotlin и общий язык с Android-частью.

## 3. Модули сервера

- `auth`: register-device, login-device, refresh, logout, token revoke.
- `accounts`: account profile, disabled accounts, optional email module hooks, future MFA hooks.
- `devices`: привязанные устройства, revoke, last seen.
- `keys`: публичные ключи устройств, fingerprints, key rotation.
- `contacts`: contact requests, accept/delete, trust state.
- `encrypted objects`: прием и хранение encrypted payload envelopes.
- `sync`: inbox, since cursor, ack, server revision.
- `live location`: sessions, encrypted updates, websocket delivery.
- `media blobs future`: encrypted media upload/download и cleanup.
- `admin/health`: health, version, basic diagnostics.
- `rate limit/audit`: ограничения запросов и приватные audit events.

## 4. Database Schema Draft

Базовая серверная сущность - `accounts`, а не user-with-email. `account_id` создается без email. `device_id` регистрируется вместе с public key, а public key fingerprint хранится на сервере для trust UI и доставки encrypted payload. Sessions и refresh tokens привязаны к `account_id` + `device_id`. Email optional; email-based login является отдельным модулем, а не базовым требованием.

Recovery phrase / recovery key используется для переноса аккаунта на новое устройство. Серверная модель recovery не должна давать серверу возможность расшифровать старые E2E-данные без recovery key пользователя.

`accounts`:

- `id`;
- `display_name nullable`;
- `callsign nullable`;
- `created_at`;
- `disabled_at`;
- `email nullable`;
- `email_verified_at nullable`;
- `email_login_enabled boolean` или server-level feature;
- `recovery_enabled boolean`.

`devices`:

- `id`;
- `account_id`;
- `device_name`;
- `created_at`;
- `revoked_at`;
- `last_seen_at`.

`public_keys`:

- `id`;
- `account_id`;
- `device_id`;
- `public_key`;
- `fingerprint`;
- `algorithm`;
- `created_at`;
- `revoked_at`.

`refresh_tokens`:

- `id`;
- `account_id`;
- `device_id`;
- `token_hash`;
- `expires_at`;
- `revoked_at`.

`recovery`:

- не хранить recovery phrase plaintext;
- если нужен серверный recovery record, хранить только hash/verifier или encrypted recovery envelope;
- конкретная схема recovery будет отдельной security task.

`contacts`:

- `id`;
- `owner_account_id`;
- `contact_account_id`;
- `display_name`;
- `trust_status`;
- `created_at`;
- `deleted_at`.

`encrypted_objects`:

- `object_id`;
- `sender_account_id`;
- `sender_device_id`;
- `recipient_account_id`;
- `object_type`;
- `encrypted_payload`;
- `nonce`;
- `key_id`;
- `created_at`;
- `updated_at`;
- `deleted_at`;
- `server_revision`;
- `delivery_state`.

`live_sessions`:

- `id`;
- `sender_account_id`;
- `recipient_account_id`;
- `status`;
- `expires_at`;
- `created_at`;
- `stopped_at`.

`live_location_updates`:

- `session_id`;
- `encrypted_payload`;
- `nonce`;
- `key_id`;
- `created_at`.

`audit_events`:

- `id`;
- `account_id nullable`;
- `event_type`;
- `ip_hash`;
- `user_agent_hash`;
- `created_at`.

## 5. API Endpoints Draft

Core account/device auth:

- `POST /auth/register-device`;
- `POST /auth/login-device`;
- `POST /auth/refresh`;
- `POST /auth/logout`;
- `POST /auth/logout-all`;
- `POST /devices/link`;
- `GET /accounts/me`;
- `GET /devices`;
- `POST /devices/{deviceId}/revoke`;
- `POST /keys`;
- `GET /keys/me`.

`POST /auth/logout` revokes the current refresh token for the current `account_id` + `device_id` session. `POST /auth/logout-all` is required for account-wide incident response and revokes all refresh tokens for the `account_id`.

`POST /devices/{deviceId}/revoke` revokes refresh tokens for the selected `device_id`, marks the device revoked, and forbids further use of that device for new requests, uploads, live updates, or key operations. Public keys that belong to a revoked device must not be used for new encrypted sends.

Email endpoints are optional future module endpoints, not a baseline server requirement:

- `POST /auth/email/register`;
- `POST /auth/email/verify`;
- `POST /auth/email/login`;
- `POST /auth/email/resend-verification`.

Contacts:

- `POST /contacts/request`;
- `POST /contacts/accept`;
- `DELETE /contacts/{id}`;
- `GET /contacts`.

Objects:

- `POST /objects`;
- `GET /objects/inbox`;
- `GET /objects/since?cursor=`;
- `DELETE /objects/{id}`;
- `POST /objects/{id}/ack`.

Live:

- `POST /live/sessions`;
- `POST /live/sessions/{id}/updates`;
- `POST /live/sessions/{id}/stop`;
- `websocket /live/ws`.

Health:

- `GET /health`;
- `GET /version`.

Server capabilities:

- `GET /api/server/capabilities`.

Contract details: [SERVER_CAPABILITIES_CONTRACT.md](SERVER_CAPABILITIES_CONTRACT.md).

Пример будущего ответа capabilities endpoint. Текущий backend skeleton может объявлять только уже реализованную часть контракта; identity flags ниже описывают целевое расширение и не реализуются в этой документационной задаче. `emailVerification` означает поддержку email verification capability, а обязательность для email-based accounts должна обозначаться отдельным future flag `emailVerificationRequired`.

```json
{
  "serverName": "Sector self-hosted",
  "operatorName": "Private operator",
  "deploymentMode": "private_self_hosted",
  "dataResidency": "unknown|ru|international|regulated",
  "cryptoProfile": "production_e2e",
  "relayOnly": true,
  "storesUserArchive": false,
  "payloadTtlSeconds": 604800,
  "mediaTtlSeconds": 604800,
  "deleteAfterDeliverySupported": true,
  "features": {
    "registration": true,
    "emailLogin": false,
    "emailVerification": false,
    "emailVerificationRequired": false,
    "noEmailAccounts": true,
    "inviteRegistration": true,
    "deviceRecovery": true,
    "recoveryPhraseRequired": true,
    "accountRecovery": true,
    "publicKeyRegistration": true,
    "fingerprintVerification": true,
    "contacts": true,
    "encryptedObjects": true,
    "encryptedMedia": true,
    "liveLocation": false,
    "cloudBackup": false,
    "webMap": false
  }
}
```

Capabilities endpoint is implemented as a public skeleton contract for future client server profile checks. It does not implement auth, relay, contacts, encrypted object sync, live location, media sync, no-email account registration, device recovery, or email-based accounts.

## 6. Authorization Rules

- Каждый endpoint проверяет `account_id` и `device_id` из token.
- Нельзя получить объект просто по `object_id` без проверки recipient/sender.
- Server-side object-level authorization обязателен для read, write, delete и ack.
- Contacts endpoints проверяют обе стороны связи и состояние контакта.
- Revoked device не может отправлять новые объекты или live updates.
- Public keys revoked device не используются для новых отправок.
- Rate limit нужен на login, register, refresh и live updates.
- Удаленный контакт не получает новые encrypted objects после `deleted_at` или revoke.

## 7. Token Policy

- Access token короткоживущий.
- Refresh token хранится на сервере только как hash.
- Refresh token привязан к `account_id` + `device_id`.
- Logout удаляет или revokes текущий refresh token.
- Logout-all отзывает все refresh tokens аккаунта.
- Device revoke отзывает refresh tokens конкретного `device_id` и запрещает дальнейшее использование устройства.
- Future MFA проектируется отдельно и не входит в первый серверный этап.

## 8. Logging And Privacy

- Не логировать `encrypted_payload` полностью.
- Не логировать координаты.
- Не логировать plaintext.
- Логировать request id, user id, endpoint, status, duration.
- IP и User-Agent можно хешировать перед записью в audit.
- Ошибки криптографии не должны раскрывать payload или помогать угадывать содержимое.
- Размер payload и routing metadata считаются metadata leakage и должны минимизироваться там, где это возможно.

## 9. Sector-Map Web

Первая версия сайта безопаснее как кабинет:

- аккаунт;
- устройства;
- контакты;
- sessions;
- health/version для self-hosted диагностики.

Web-карта с приватными объектами возможна только если расшифрование происходит в браузере. Сервер не должен отдавать plaintext даже web-клиенту, если включен E2E. Хранение приватного ключа в браузере — отдельная security-задача, которую нельзя считать решенной автоматически.

На первом серверном этапе лучше не делать web-карту приватных E2E-данных. Если web sector-map нужен раньше, он должен работать только с неперсональными или явно публичными данными, не смешанными с private sync.

## local backup policy

Постоянный backup пользовательских данных делается локально на клиенте. Серверный cloud backup пользовательского архива не реализуется как базовая функция и не должен подменять local-first модель.

Требования к будущему local backup:

- формат будущий: encrypted sector backup zip;
- backup должен быть зашифрован паролем или recovery key;
- пользователь сам хранит backup и выбирает способ переноса;
- сервер не должен иметь ключей для расшифровки backup;
- сервер не должен автоматически получать или хранить пользовательский backup archive.

Серверные backups допустимы для служебной базы, конфигурации и audit logs. Они не должны становиться постоянным backup пользовательского payload. Если временный encrypted payload технически попадает в backup служебного слоя, retention должен быть минимальным и явно описан в policy.

## legal/data residency responsibilities

Сервер должен явно объявлять operator info, deployment mode и data residency через capabilities, чтобы клиент мог показать пользователю предупреждения до отправки sensitive payload.

Распределение ответственности:

- official RF server - ответственность оператора official server;
- custom/self-hosted server - ответственность владельца или администратора сервера;
- regulated self-hosted - ответственность организации или заказчика;
- приложение должно показывать пользователю, кто заявлен оператором сервера;
- разработчик клиента не должен скрыто получать данные custom/self-hosted серверов;
- обход 152-ФЗ запрещен;
- encrypted payload не отменяет requirements по служебным персональным данным и metadata.

Для foreign server и данных, связанных с РФ, клиент должен показывать legal/data residency warning. Для dev/noop сервера реальные персональные данные, координаты, заметки, контакты и live location запрещены.

## 10. Этапы реализации

0. Документация и протоколы.
1. Backend skeleton.
2. Auth, accounts, devices, public keys.
3. Contacts.
4. Encrypted object upload/download.
5. Android sync client.
6. Live location sessions.
7. Encrypted media.
8. Sector-map web.

## 11. Что не реализовывать в первом серверном этапе

В первом серверном этапе не реализовывать:

- live location;
- web-карту приватных данных;
- восстановление ключей;
- группы;
- публичные карты;
- media sync;
- сложный conflict resolution;
- автоматическую синхронизацию всего без ручного контроля.
