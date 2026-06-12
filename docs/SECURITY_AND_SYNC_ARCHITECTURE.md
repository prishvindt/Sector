# Архитектура безопасности и синхронизации

Серверная часть этой модели отдельно описана в [SERVER_BACKEND_ARCHITECTURE.md](SERVER_BACKEND_ARCHITECTURE.md). Этот документ фиксирует требования безопасности для Android-клиента, локальной модели, будущей синхронизации, контактов и E2E-шифрования.

## Зачем нужен `sector_objects`

`sector_objects` — единая локальная таблица для данных, которые позже можно будет переносить между устройствами, контактами, сервером или зашифрованными message-bundle. Старые Room-таблицы `measurements` и `imported_locations` хранили близкие по смыслу данные в разных структурах. Из-за этого будущие контакты, синхронизация, live location, заметки на карте и зашифрованный обмен потребовали бы новые таблицы и отдельные экспортные пути.

Новая модель хранит один envelope на объект:

- стабильный `object_id` UUID;
- строковую metadata: `object_type`, `owner_kind`, `source_kind`, `sync_state`, `visibility`, `encryption_state`;
- lifecycle-поля `created_at`, `updated_at`, `deleted_at`;
- версионированный `payload_json`.

Envelope — это metadata для sync и владения. `payload_json` — тело объекта. Оно специально хранится одним JSON-блоком, чтобы будущий crypto-слой мог шифровать payload целиком.

## Object Types

- `AZIMUTH_RAY`: азимутный луч на карте.
- `SHARED_LOCATION`: переданная GPS-точка.
- `MAP_NOTE`: локальная заметка на карте с текстом и metadata фото/аудио вложений.
- `LIVE_LOCATION`: будущая live-позиция; live sharing пока не реализован.
- `UNKNOWN`: безопасный fallback для будущих или неподдержанных типов.

## Payload JSON

Payload-и версионируются отдельно от таблицы:

- `AzimuthRayPayloadV1`: `latitude`, `longitude`, `azimuth`, `error`, `distanceKm`, nullable `callsign`.
- `SharedLocationPayloadV1`: `latitude`, `longitude`, nullable `accuracyMeters`, nullable `bearing`, `timestamp`, nullable `callsign`.
- `MapNotePayloadV1`: `latitude`, `longitude`, `title`, `text`, `createdAt`, `updatedAt`, `attachments`.
- `MapNoteAttachmentPayloadV1`: `attachmentId`, `type` (`PHOTO` или `AUDIO`), относительный `localPath`, `mimeType`, `sizeBytes`, nullable `durationMs`, `createdAt`, `mediaIncluded`.
- `LiveLocationPayloadV1`: `latitude`, `longitude`, nullable `accuracyMeters`, nullable `bearing`, nullable `speed`, `timestamp`, nullable `sessionId`, nullable `callsign`.

Известные payload-и декодируются и валидируются перед сохранением. Неизвестные object types сохраняют raw `payload_json`, но карта их не отображает.

## Цели безопасности

Будущая серверная синхронизация должна исходить из таких целей:

- сервер не должен читать содержимое объектов;
- перехватчик сети не должен читать координаты, заметки, азимуты, live location или медиа;
- чужой пользователь не должен получать доступ к объектам, которые не адресованы ему;
- украденный access token должен иметь короткий срок действия и не должен давать бессрочный доступ;
- удаленный контакт не должен получать новые данные после удаления или revoke;
- live location не должна храниться бесконечно;
- медиа заметок не должны уходить на сервер открытыми.

TLS обязателен для транспорта, но не заменяет E2E. Даже при TLS серверная БД и серверные администраторы не должны видеть plaintext payload.

## Модель угроз

Минимальная модель угроз для будущего сервера:

- посторонний пользователь пытается получить чужой объект по `object_id`, cursor или endpoint;
- access token скомпрометирован и используется до истечения срока действия;
- баг object-level authorization позволяет читать или удалять объект без проверки sender/recipient;
- администратор сервера или утечка БД получает доступ к stored data;
- перехватчик сети видит трафик или пытается повторить запрос;
- телефон потерян, а локальная база и ключи могут быть извлечены без защиты устройства;
- старый encrypted payload повторно отправляется на сервер;
- контакт или ключ подменяются до проверки fingerprint;
- routing metadata, timestamps, размеры payload и частота live updates раскрывают часть поведения пользователя.

Эта модель не закрывает все возможные сценарии. Она задает нижнюю планку для первого серверного дизайна.

## Что сервер видит и чего не видит

Сервер может видеть минимальные данные, нужные для авторизации, доставки и синхронизации:

- `sender_id`;
- `recipient_id`;
- `object_id`;
- `object_type`;
- timestamps;
- размер encrypted payload;
- routing metadata.

Сервер не должен видеть:

- координаты;
- текст заметки;
- азимут;
- погрешность;
- мощность;
- позывной внутри payload, если он относится к приватным данным;
- live location contents;
- фото и аудио заметки.

Если для какой-то функции нужен plaintext на сервере, такая функция должна быть вынесена в отдельное архитектурное решение и не должна смешиваться с E2E private sync.

## Ключи и идентичность

Будущая модель идентичности должна ввести отдельные сущности:

- `account_id`: серверная учетная запись пользователя;
- `device_id`: конкретное устройство пользователя;
- `contact_id`: локальная или серверная связь с другим пользователем;
- identity public key;
- identity private key;
- key fingerprint;
- `trust_status`.

Приватный ключ остается на устройстве и не отправляется на сервер. Публичный ключ может храниться на сервере для доставки encrypted payload получателям. Сервер не хранит приватные ключи и не должен иметь материал, достаточный для расшифрования payload.

Контакты должны добавляться через QR, код или ссылку с fingerprint. Пользователь должен видеть trust state контакта и иметь возможность заметить смену ключа. Подмена контакта или ключа должна переводить связь в состояние, требующее повторного подтверждения.

## Идентичность объекта

`object_id` — UUID string и primary key. Он должен переживать export, import, server upload, conflict handling и encrypted sharing. Локальные объекты генерируют новый UUID. Bundle-import сохраняет входящий UUID, если он валиден.

## Soft Delete

Обычное удаление выставляет `deleted_at`; строка физически не удаляется. Активные запросы по умолчанию фильтруют `deleted_at IS NULL`. Это оставляет tombstone для будущей серверной синхронизации и conflict resolution. Физическое удаление используется только при полной очистке базы или destructive-переходе со старой схемы.

Для `MAP_NOTE` на текущем этапе есть исключение по медиафайлам: строка объекта удаляется через soft delete, но локальная папка `files/notes/{objectId}` удаляется физически сразу. Серверной синхронизации медиа пока нет, поэтому отдельные tombstone для файлов не ведутся.

## Будущая серверная синхронизация

Серверной синхронизации сейчас нет. Планируемый будущий поток:

1. Локальные изменения создают или обновляют `sector_objects`.
2. `sync_state` переходит через `PENDING_UPLOAD`, `SYNCED`, `FAILED` или `CONFLICT`.
3. Sync worker отправляет только объекты, разрешенные `visibility`.
4. Перед upload `payload_json` шифруется на устройстве отправителя.
5. Сервер хранит object envelope и encrypted payload blobs там, где включено шифрование.
6. Удаленные объекты синхронизируются как tombstone через `deleted_at`.

Медиа вложения заметок пока не синхронизируются. Будущий sync должен отдельно определить, как хранить encrypted media blobs, как переносить attachment metadata и как удалять/восстанавливать файлы при конфликте.

Базовые sync states:

- `LOCAL_ONLY`: объект существует только на устройстве и не планируется к upload;
- `PENDING_UPLOAD`: объект ожидает отправки;
- `SYNCED`: сервер подтвердил актуальную ревизию;
- `FAILED`: upload/download завершился ошибкой и требует повтора или решения пользователя;
- `CONFLICT`: локальная и серверная версии расходятся;
- soft delete / tombstone: `deleted_at` доставляет факт удаления без немедленного физического удаления строки;
- `updated_at`: локальное время последнего изменения, полезное для UI и первичного conflict handling;
- server revision: серверный монотонный revision/cursor для доставки изменений.

Last writer wins допустим только как первый простой вариант для некритичных объектов. Конфликты заметок, вложений и live/session-state лучше проектировать отдельно, чтобы не терять пользовательский текст или связь с медиа.

## Будущие контакты

Контакты сейчас не реализованы. Envelope уже содержит `owner_kind`, `owner_id` и `device_id`, чтобы будущие объекты контактов могли сосуществовать с локальными объектами. При импорте bundle объект, который у отправителя был `ownerKind = ME`, локально сохраняется как объект контакта.

Серверные contacts endpoints должны проверять обе стороны связи. Удаленный или revoked контакт не должен получать новые encrypted objects, новые live updates или новые media blobs.

## Будущее E2E-шифрование

Реального шифрования сейчас нет. В коде есть только интерфейсы `CryptoManager` и `NoOpCryptoManager`. Методы contact encryption намеренно возвращают failure; local no-op методы возвращают исходный текст.

Будущая архитектура:

- `payload_json` шифруется на устройстве отправителя;
- сервер получает `encrypted_payload`;
- получатель расшифровывает payload локально;
- собственную криптографию писать нельзя;
- `CryptoManager` или будущий `CryptoService` должен остаться интерфейсом между domain-layer и выбранным протоколом;
- реальная библиотека или протокол выбираются отдельной задачей;
- `NoOpCryptoManager` допустим только для локального/dev режима и тестов, но не для production sync.

Серверу не нужно понимать координаты, азимуты, заметки, позывные или live location internals. Для contact sharing сервер должен хранить только encrypted blobs и минимальную routing metadata, нужную для доставки.

## Будущий encrypted bundle

Планируемый формат `SECTOR_ENCRYPTED_BUNDLE_V1` должен быть описан как envelope для encrypted payload. Он не реализуется в коде в этой задаче.

Формат должен содержать:

- `format`;
- `version`;
- sender account/device info;
- recipients;
- `key_id` или fingerprint;
- cipher suite;
- nonce;
- encrypted payload;
- signature или authentication tag, если это предусмотрено выбранной схемой.

Планируемый формат:

```json
{
  "format": "SECTOR_ENCRYPTED_BUNDLE_V1",
  "version": 1,
  "sender": {
    "contactId": "...",
    "deviceId": "..."
  },
  "recipients": ["..."],
  "cipher": {
    "algorithm": "...",
    "nonce": "...",
    "payload": "..."
  }
}
```

Этот формат только задокументирован. В коде он не реализован, потому что в текущей задаче нет contact identity, key exchange и реального crypto layer.

## Live location security

`LIVE_LOCATION` — будущий тип, но live sharing должен быть отдельной session, а не обычным бессрочным объектом.

Требования:

- session имеет `expires_at`;
- получатель выбирается явно;
- по умолчанию хранится только последняя encrypted live point;
- история перемещений не хранится без отдельного явного режима;
- после stop/expire новые точки не принимаются или не доставляются;
- live payload шифруется так же строго, как обычный приватный объект;
- live location нельзя включать до accounts, contacts и production-ready crypto.

## Media attachments

Сейчас медиа заметок локальные: фото и аудио лежат во внутреннем хранилище приложения, а в `MapNotePayloadV1` хранится metadata и относительный `localPath`.

Будущая серверная media sync должна:

- шифровать bytes до upload;
- хранить на сервере encrypted media blob;
- держать attachment metadata внутри encrypted payload, если metadata раскрывает приватный контекст;
- иметь tombstone/cleanup policy для удаления файлов;
- не использовать base64 в Room и UI-state;
- не отправлять фото или аудио заметки на сервер открытыми.

Реальное шифрование media attachments не реализовано в этом этапе и должно проектироваться вместе с серверной синхронизацией медиа.

## Что запрещено

Для будущей серверной архитектуры запрещено:

- отправлять открытые координаты на сервер;
- хранить plaintext payload на сервере;
- использовать IMEI, Android ID или номер телефона как основной стабильный id;
- хранить пароли без `argon2id` или `bcrypt`;
- писать самодельную криптографию;
- делать live sharing до accounts, contacts и crypto;
- делать web-карту с приватными данными без клиентского расшифрования.

## Что реализовано сейчас

- Room-таблица `sector_objects`.
- Destructive-переход со старых локальных `measurements` / `imported_locations`.
- Domain enums и payload-модели.
- `SectorObjectDao` и `SectorObjectRepository`.
- Legacy-import из `SECTOR_MEASUREMENT_V1` и `SECTOR_LOCATION_V1`.
- Export/import `SECTOR_BUNDLE_V1`.
- Локальные `MAP_NOTE` через `sector_objects`, включая metadata фото/аудио вложений.
- Отрисовка карты из view-моделей, полученных из `sector_objects`.
- `MapObjectVisibilityPolicy`.
- Crypto interfaces без реального шифрования.

## Что намеренно не реализовано

- Сервер.
- Регистрация, авторизация, contacts UI, QR contacts.
- Реальное E2E-шифрование.
- Live location sharing transport.
- Серверная синхронизация и шифрование media attachments.
- WebSocket, MQTT, polling, Firebase, analytics, Crashlytics.
- Новые тяжелые зависимости.

## Следующие этапы

1. Добавить contact identity и device identity.
2. Добавить реальное управление ключами с platform-backed storage.
3. Реализовать encrypted payload и encrypted bundle.
4. Добавить server sync API и conflict policy.
5. Добавить UI для object visibility и sharing permissions.
6. Добавить UI заметок на карте.
7. Добавить live location sessions и transport только после encryption и contacts.
