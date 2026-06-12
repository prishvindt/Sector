# Форматы обмена

Обмен в «Секторе» выполняется вручную через системный share, буфер обмена или вставку текста в окно импорта. Приложение не отправляет координаты автоматически.

## Основной формат: `SECTOR_BUNDLE_V1`

`SECTOR_BUNDLE_V1` — JSON-формат для передачи одного или нескольких `sector_objects`.

Пример:

```json
{
  "format": "SECTOR_BUNDLE_V1",
  "version": 1,
  "createdAt": 1779556500000,
  "sender": {
    "callsign": "R2ABC",
    "deviceId": "device-local"
  },
  "objects": [
    {
      "objectId": "550e8400-e29b-41d4-a716-446655440000",
      "objectType": "AZIMUTH_RAY",
      "ownerKind": "ME",
      "ownerId": null,
      "deviceId": "device-local",
      "sourceKind": "LOCAL",
      "createdAt": 1779556500000,
      "updatedAt": 1779556500000,
      "deletedAt": null,
      "syncState": "LOCAL_ONLY",
      "visibility": "SHAREABLE",
      "encryptionState": "PLAIN_LOCAL",
      "payloadVersion": 1,
      "payload": {
        "latitude": 55.123456,
        "longitude": 37.123456,
        "azimuth": 123.0,
        "error": 15.0,
        "distanceKm": 15.0,
        "callsign": "R2ABC"
      }
    }
  ]
}
```

Правила:

- `format` должен быть `SECTOR_BUNDLE_V1`;
- `version` сейчас равен `1`;
- `objects` может содержать один или несколько объектов;
- `objectId` должен быть UUID;
- известные payload-и валидируются перед сохранением;
- неизвестный `objectType` можно импортировать как raw object, но карта его не отображает;
- при импорте bundle объект, который у отправителя был `ownerKind = ME`, локально становится объектом контакта.

## Payload V1

`AZIMUTH_RAY`:

```json
{
  "latitude": 55.123456,
  "longitude": 37.123456,
  "azimuth": 123.0,
  "error": 15.0,
  "distanceKm": 15.0,
  "callsign": "R2ABC"
}
```

`SHARED_LOCATION`:

```json
{
  "latitude": 55.123456,
  "longitude": 37.123456,
  "accuracyMeters": 8.0,
  "bearing": null,
  "timestamp": 1710000000,
  "callsign": "R2ABC"
}
```

`MAP_NOTE`:

```json
{
  "latitude": 55.123456,
  "longitude": 37.123456,
  "title": "Заметка 1",
  "text": "Описание точки",
  "createdAt": 1779556500000,
  "updatedAt": 1779556500000,
  "attachments": [
    {
      "attachmentId": "photo-1",
      "type": "PHOTO",
      "localPath": "",
      "mimeType": "image/jpeg",
      "sizeBytes": 120000,
      "durationMs": null,
      "createdAt": 1779556500000,
      "mediaIncluded": false
    }
  ]
}
```

Локально `localPath` хранится как относительный путь внутри app internal files, например `notes/{objectId}/photo_1.jpg`. При экспорте через messenger медиафайлы не прикладываются: `localPath` очищается, `mediaIncluded` становится `false`, а bytes/base64 фото или аудио не попадают в текст bundle.

При импорте `MAP_NOTE`:

- заметка без медиа создается как обычная заметка;
- attachment metadata без физического файла импортируется как отсутствующее медиа и не должно приводить к падению;
- неподдержанные или битые attachment-блоки пропускаются при декодировании payload.

`LIVE_LOCATION` payload описан в коде как подготовка архитектуры, но UI/transport для него пока не реализованы.

## Legacy import: `SECTOR_MEASUREMENT_V1`

Старый текстовый формат азимутного луча поддерживается только для импорта. При импорте он сохраняется как `sector_objects.object_type = AZIMUTH_RAY`.

```text
SECTOR_MEASUREMENT_V1
measurement_id=550e8400-e29b-41d4-a716-446655440000
callsign=R2ABC
lat=55.123456
lon=37.123456
azimuth_deg=123.0
azimuth_error_deg=15.0
distance_km=15.0
timestamp=2026-05-23T20:15:00+03:00
```

Один текст может содержать несколько блоков `SECTOR_MEASUREMENT_V1`. Валидные блоки импортируются, битые пропускаются. Если все блоки битые, импорт завершается ошибкой. Старое поле `range_km` читается как расстояние для совместимости; если ни `distance_km`, ни `range_km` нет, импорт использует fallback `15 км`. Старое `signal_dbm` не используется как расстояние.

## Legacy import: `SECTOR_LOCATION_V1`

Старый текстовый формат GPS-точки поддерживается только для импорта. При импорте он сохраняется как `sector_objects.object_type = SHARED_LOCATION`.

```text
SECTOR_LOCATION_V1
callsign=R2ABC
latitude=55.123456
longitude=37.123456
accuracyMeters=8.0
timestamp=1710000000
```

Если позывной непустой, новая legacy GPS-точка от того же позывного soft-delete-ит предыдущую активную точку этого владельца и становится текущей импортированной точкой.

## Будущий encrypted bundle

`SECTOR_ENCRYPTED_BUNDLE_V1` пока не реализован. Его будущая архитектура описана в [SECURITY_AND_SYNC_ARCHITECTURE.md](SECURITY_AND_SYNC_ARCHITECTURE.md).
