# Архитектура безопасности и синхронизации

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

- `AzimuthRayPayloadV1`: `latitude`, `longitude`, `azimuth`, `error`, nullable `signal`, nullable `callsign`.
- `SharedLocationPayloadV1`: `latitude`, `longitude`, nullable `accuracyMeters`, nullable `bearing`, `timestamp`, nullable `callsign`.
- `MapNotePayloadV1`: `latitude`, `longitude`, `title`, `text`, `createdAt`, `updatedAt`, `attachments`.
- `MapNoteAttachmentPayloadV1`: `attachmentId`, `type` (`PHOTO` или `AUDIO`), относительный `localPath`, `mimeType`, `sizeBytes`, nullable `durationMs`, `createdAt`, `mediaIncluded`.
- `LiveLocationPayloadV1`: `latitude`, `longitude`, nullable `accuracyMeters`, nullable `bearing`, nullable `speed`, `timestamp`, nullable `sessionId`, nullable `callsign`.

Известные payload-и декодируются и валидируются перед сохранением. Неизвестные object types сохраняют raw `payload_json`, но карта их не отображает.

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
4. Сервер хранит object envelope и encrypted payload blobs там, где включено шифрование.
5. Удаленные объекты синхронизируются как tombstone через `deleted_at`.

Медиа вложения заметок пока не синхронизируются. Будущий sync должен отдельно определить, как хранить encrypted media blobs, как переносить attachment metadata и как удалять/восстанавливать файлы при конфликте.

## Будущие контакты

Контакты сейчас не реализованы. Envelope уже содержит `owner_kind`, `owner_id` и `device_id`, чтобы будущие объекты контактов могли сосуществовать с локальными объектами. При импорте bundle объект, который у отправителя был `ownerKind = ME`, локально сохраняется как объект контакта.

## Будущее E2E-шифрование

Реального шифрования сейчас нет. В коде есть только интерфейсы `CryptoManager` и `NoOpCryptoManager`. Методы contact encryption намеренно возвращают failure; local no-op методы возвращают исходный текст.

В будущем нужно шифровать `payload_json` целиком. Серверу не нужно понимать координаты, азимуты, заметки, позывные или live location internals. Для contact sharing сервер должен хранить только encrypted blobs и минимальную routing metadata, нужную для доставки.

Фото и аудио заметок сейчас лежат локально открытыми файлами во внутреннем хранилище приложения. Реальное шифрование media attachments не реализовано в этом этапе и должно проектироваться вместе с серверной синхронизацией медиа.

## Будущий encrypted sharing через мессенджеры

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
