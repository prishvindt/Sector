# Серверная архитектура Sector

Этот документ описывает будущий сервер Sector. Требования к E2E, encrypted payload, live location и media sync связаны с [SECURITY_AND_SYNC_ARCHITECTURE.md](SECURITY_AND_SYNC_ARCHITECTURE.md), а deployment/server modes и client server profiles - с [CRYPTO_SECURITY_PROFILE.md](CRYPTO_SECURITY_PROFILE.md). Серверная часть пока не реализуется в коде приложения в рамках этой документационной задачи.

## 1. Назначение сервера

Сервер нужен как транспортный и учетный слой поверх local-first Android-приложения. Он не заменяет локальную Room-модель `sector_objects` и не должен делать базовую работу приложения зависимой от интернета.

Сервер:

- регистрирует пользователей;
- хранит аккаунты и устройства;
- хранит публичные ключи;
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

- `auth`: register, login, refresh, logout, token revoke.
- `users`: account profile, disabled users, future MFA hooks.
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

`users`:

- `id`;
- `login/email`;
- `password_hash`;
- `created_at`;
- `disabled_at`.

`devices`:

- `id`;
- `user_id`;
- `device_name`;
- `public_key_id`;
- `created_at`;
- `revoked_at`;
- `last_seen_at`.

`public_keys`:

- `id`;
- `user_id`;
- `device_id`;
- `public_key`;
- `fingerprint`;
- `algorithm`;
- `created_at`;
- `revoked_at`.

`contacts`:

- `id`;
- `owner_user_id`;
- `contact_user_id`;
- `display_name`;
- `trust_status`;
- `created_at`;
- `deleted_at`.

`encrypted_objects`:

- `object_id`;
- `sender_user_id`;
- `sender_device_id`;
- `recipient_user_id`;
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
- `sender_user_id`;
- `recipient_user_id`;
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

`refresh_tokens`:

- `id`;
- `user_id`;
- `device_id`;
- `token_hash`;
- `expires_at`;
- `revoked_at`.

`audit_events`:

- `id`;
- `user_id nullable`;
- `event_type`;
- `ip_hash`;
- `user_agent_hash`;
- `created_at`.

## 5. API Endpoints Draft

Auth:

- `POST /auth/register`;
- `POST /auth/login`;
- `POST /auth/refresh`;
- `POST /auth/logout`;
- `POST /auth/logout-all`.

Devices:

- `GET /devices`;
- `POST /devices`;
- `DELETE /devices/{id}`.

Keys:

- `POST /keys`;
- `GET /users/{id}/keys`.

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

Пример ответа будущего endpoint:

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
    "emailVerification": true,
    "contacts": true,
    "encryptedObjects": true,
    "encryptedMedia": true,
    "liveLocation": false,
    "cloudBackup": false,
    "webMap": false
  }
}
```

Endpoint draft нужен для будущей проверки client server profile. Реализовывать его в текущей документационной задаче не нужно.

## 6. Authorization Rules

- Каждый endpoint проверяет `user_id` из token.
- Нельзя получить объект просто по `object_id` без проверки recipient/sender.
- Server-side object-level authorization обязателен для read, write, delete и ack.
- Contacts endpoints проверяют обе стороны связи и состояние контакта.
- Revoked device не может отправлять новые объекты или live updates.
- Rate limit нужен на login, register, refresh и live updates.
- Удаленный контакт не получает новые encrypted objects после `deleted_at` или revoke.

## 7. Token Policy

- Access token короткоживущий.
- Refresh token хранится на сервере только как hash.
- Refresh token привязан к device.
- Logout удаляет или revokes текущий refresh token.
- Logout-all отзывает все refresh tokens пользователя.
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
2. Auth, users, devices, public keys.
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
