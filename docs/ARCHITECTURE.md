# Архитектура проекта «Сектор»

## Назначение

«Сектор» — Android-приложение для полевой работы с GPS-точкой, азимутными лучами, импортированными GPS-точками, маршрутами и ручным обменом данными. Карта работает через Yandex MapKit. Пользовательские координаты и объекты карты хранятся локально и не отправляются на сервер автоматически.

## Основные пакеты

- `data/`: Room, DataStore, `SectorObjectEntity`, `SectorObjectDao`, `SectorObjectRepository`, настройки и view/domain-модели совместимости `Measurement` / `ImportedLocation`.
- `domain/objects/`: стабильные enum-типы, payload-модели, JSON codec и `SECTOR_BUNDLE_V1`.
- `domain/crypto/`: `CryptoManager` и `NoOpCryptoManager` без реального шифрования.
- `domain/measurements/`: `MeasurementManager`, который создает, импортирует, экспортирует и удаляет азимутные лучи через `SectorObjectRepository`.
- `domain/locations/`: `LocationShareManager`, который экспортирует текущую GPS-точку bundle-форматом и импортирует legacy `SECTOR_LOCATION_V1`.
- `map/`: Yandex MapKit, `MapObjectsController`, `MapObjectVisibilityPolicy`, `RoutePlanner`.
- `ui/`: Compose UI, `MainViewModel`, экраны, диалоги, drawer, настройки.
- `updates/`: проверка `update.json`, загрузка APK и запуск системного установщика.
- `domain/telemetry/` и `telemetry/`: минимальная техническая телеметрия без координат, азимутов и позывных.

## Единая модель данных

Главная Room-таблица теперь одна:

```text
sector_objects
```

Колонки:

- `object_id TEXT PRIMARY KEY`: UUID объекта.
- `object_type TEXT`: `AZIMUTH_RAY`, `SHARED_LOCATION`, `MAP_NOTE`, `LIVE_LOCATION` или будущий/неизвестный тип.
- `owner_kind TEXT`: `ME`, `CONTACT`, `UNKNOWN`.
- `owner_id TEXT NULL`.
- `device_id TEXT NULL`.
- `source_kind TEXT`: `LOCAL`, `IMPORTED_MESSAGE`, `SERVER`, `LIVE`.
- `created_at INTEGER`.
- `updated_at INTEGER`.
- `deleted_at INTEGER NULL`.
- `sync_state TEXT`: `LOCAL_ONLY`, `PENDING_UPLOAD`, `SYNCED`, `FAILED`, `CONFLICT`.
- `visibility TEXT`: `PRIVATE`, `SHAREABLE`, `SHARED_WITH_CONTACTS`.
- `encryption_state TEXT`: `PLAIN_LOCAL`, `ENCRYPTED_LOCAL`, `ENCRYPTED_FOR_CONTACTS`, `UNSUPPORTED`.
- `payload_version INTEGER`.
- `payload_json TEXT`.

`payload_json` хранит конкретные данные объекта и спроектирован так, чтобы в будущем шифроваться целиком.

## Что стало со старыми моделями

`Measurement` и `ImportedLocation` больше не являются Room entity и не имеют DAO/repository. Они оставлены как обычные модели отображения и совместимости, чтобы существующий UI карты, списка замеров и расчет пересечения не требовали отдельного большого переписывания.

Старые таблицы `measurements` и `imported_locations` удалены из активной Room-модели. Новые данные пишутся только через `SectorObjectRepository`.

## Room Version

Текущая Room schema version: `3`.

Переход со старых схем destructive по данным:

- migration `1 -> 2` оставлена для старого пути, где создавалась `imported_locations`;
- migration `2 -> 3` drop-ает `measurements` и `imported_locations`, затем создает `sector_objects`;
- для старой dev-схемы с `user_version = 3`, но без `sector_objects`, приложение перед открытием Room удаляет несовместимый `sector.db`, потому что данные старой локальной модели разрешено стереть.

`fallbackToDestructiveMigration()` не используется.

## Потоки данных

Создание моего азимутного луча:

```text
MeasurementInputDialog
-> MainViewModel.saveMeasurement
-> MeasurementManager
-> SectorObjectRepository.createLocalAzimuthRay
-> sector_objects
-> observeActiveAzimuthRays
-> MainUiState.measurements
-> MapObjectsController / MeasurementsScreen
```

Импорт legacy азимутного луча:

```text
SECTOR_MEASUREMENT_V1
-> ExportFormat.parseMany
-> MeasurementManager
-> SectorObjectRepository.importAzimuthRayFromLegacy
-> sector_objects object_type=AZIMUTH_RAY
```

Импорт legacy GPS-точки:

```text
SECTOR_LOCATION_V1
-> LocationExchangeFormat.parse
-> LocationShareManager
-> SectorObjectRepository.importSharedLocationFromLegacy
-> sector_objects object_type=SHARED_LOCATION
```

Экспорт:

```text
MainViewModel
-> MeasurementManager / LocationShareManager
-> SectorObjectRepository.exportObjects
-> SECTOR_BUNDLE_V1
```

Импорт нового bundle:

```text
SECTOR_BUNDLE_V1 JSON
-> SectorBundleFormat.parse
-> SectorObjectRepository.importObjectsFromBundle
-> sector_objects
```

## Карта

Карта получает данные через `SectorObjectRepository`:

- `AZIMUTH_RAY` преобразуется в `Measurement` view-модель и рисуется как азимутный луч;
- `SHARED_LOCATION` от контактов преобразуется в `ImportedLocation` view-модель и рисуется как импортированная GPS-точка;
- `MAP_NOTE`, `LIVE_LOCATION` и неизвестные типы сейчас не отображаются и не должны приводить к падению.

`MapObjectVisibilityPolicy` отвечает за базовую видимость и подписи:

- показывать ли объект текущего типа;
- показывать ли мой позывной;
- показывать ли чужие позывные;
- какую подпись вернуть для маркера.

`MapObjectsController` обновляет отдельные коллекции MapKit и не использует глобальный `map.mapObjects.clear()` при обычном update карты.

## Форматы обмена

Основной формат экспорта: `SECTOR_BUNDLE_V1`.

Bundle передает несколько объектов сразу. Один объект также экспортируется как bundle с одним элементом массива `objects`.

Legacy-импорт сохранен:

- `SECTOR_MEASUREMENT_V1` импортируется как `AZIMUTH_RAY`;
- `SECTOR_LOCATION_V1` импортируется как `SHARED_LOCATION`.

Legacy-экспорт заменен bundle-экспортом.

## Безопасность и будущая синхронизация

Подробно описано в [SECURITY_AND_SYNC_ARCHITECTURE.md](SECURITY_AND_SYNC_ARCHITECTURE.md).

Коротко:

- серверной синхронизации сейчас нет;
- контактов сейчас нет;
- live location sharing сейчас нет;
- реального E2E-шифрования сейчас нет;
- `CryptoManager` — только интерфейс будущего слоя;
- будущий сервер должен хранить encrypted payload blobs, а не открытые координаты.

## Версия приложения

Актуальная версия приложения в Gradle:

- `versionName = 0.1.8`;
- `versionCode = 9`.

`update.json` не должен меняться в архитектурных задачах ветки feature.
