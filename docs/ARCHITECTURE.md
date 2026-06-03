# Архитектура проекта «Сектор»

## 1. Назначение проекта

«Сектор» — Android-приложение для полевой работы с GPS-точкой, азимутными лучами, секторами погрешности, импортированными точками, маршрутами и обновлениями вне Google Play.

Приложение использует Yandex MapKit для карты, объектов карты и построения маршрутов. Основные пользовательские данные хранятся локально. Координаты, замеры и импортированные GPS-точки не отправляются на сервер приложения автоматически.

Сетевые обращения сейчас относятся к Yandex MapKit, проверке `update.json` и загрузке APK обновления.

## 2. Ветки и релизная модель

Подробный процесс описан в [RELEASE.md](RELEASE.md).

- `develop` — тестовая ветка.
- `main` — релизная ветка.
- `feature/*`, `docs/*` и похожие ветки — временные ветки для отдельных задач.
- Перед merge `develop -> main` нужна ручная проверка.
- `versionName` и `versionCode` поднимаются до релиза.
- GitHub Release создаётся после merge в `main`.
- `update.json` лежит в `main` и обновляется только после публикации APK.

## 3. Структура пакетов

Базовый путь исходников:

```text
app/src/main/java/com/prishvindt/sector/
```

- `data/`
  Room, DataStore, репозитории, локальные настройки, азимутные замеры и импортированные location-only точки.
  Основные классы: `AppDatabase`, `Measurement`, `MeasurementDao`, `MeasurementRepository`, `ImportedLocation`, `ImportedLocationDao`, `ImportedLocationRepository`, `MeasurementColor`, `SettingsRepository`.

- `domain/`
  Чистая доменная логика: геометрия, расчёт сектора, пересечения, текстовые форматы обмена, merge-логика и модели целей.
  Основные классы: `GeoMath`, `SectorCalculator`, `BearingIntersection`, `IntersectionTargetCalculator`, `ExportFormat`, `LocationExchangeFormat`, `MeasurementMerge`, `RouteTarget`.

- `domain/measurements/`
  Сценарии создания, импорта и экспорта азимутных замеров поверх репозитория.
  Основной класс: `MeasurementManager`.

- `domain/locations/`
  Ручной обмен текущей GPS-точкой без азимута.
  Основной класс: `LocationShareManager`.

- `domain/routes/`
  Доменная логика выбора цели маршрута и внешних ссылок.
  Основной класс: `RouteTargetManager`.

- `location/`
  GPS, GNSS-спутники, активный поиск и foreground service.
  Основные классы: `LocationTracker`, `GnssSatelliteTracker`, `LocationState`, `ActiveSearchService`.

- `map/`
  Yandex MapKit, отрисовка объектов карты, обработка тапов и построение маршрутов.
  Основные классы: `YandexMapComposable`, `MapObjectsController`, `MapTapHandler`, `MapStyle`, `RoutePlanner`.

- `ui/`
  Compose UI, `MainScreen`, `MainViewModel`, drawer, dialogs, overlays, настройки, импорт, ввод азимута, список замеров и экран о приложении.
  Подпакеты: `about/`, `callsign/`, `common/`, `drawer/`, `firststart/`, `importdata/`, `input/`, `map/`, `measurements/`, `settings/`.

- `updates/`
  Проверка `update.json`, состояние обновления, загрузка APK и запуск системного установщика.
  Основные классы: `UpdateRepository`, `UpdateChecker`, `UpdateCoordinator`, `UpdateInstaller`, `UpdateInfo`, `UpdateStatus`.

- `service/`
  Android service-компоненты вне GPS-пакета.
  Сейчас содержит `ExternalActionService`.

Корневые классы:

- `SectorApplication` — инициализация MapKit, Room и контейнера зависимостей.
- `MainActivity` — Android entry point и связывание Activity с Compose.

## 4. Потоки данных

GPS:

```text
LocationTracker / ActiveSearchService
-> MainViewModel
-> MainUiState.locationState
-> MainScreen / MapOverlays
-> YandexMapComposable / MapObjectsController
```

Азимутные замеры:

```text
Room / MeasurementDao
-> MeasurementRepository
-> MeasurementManager / MainViewModel
-> MainUiState.measurements
-> карта / список / экспорт
```

Импортированные GPS-точки без азимута:

```text
SECTOR_LOCATION_V1
-> LocationExchangeFormat
-> LocationShareManager
-> ImportedLocationRepository / ImportedLocationDao
-> MainUiState.importedLocations
-> MapObjectsController
```

Экспорт и импорт азимутных лучей:

```text
SECTOR_MEASUREMENT_V1
-> ExportFormat
-> MeasurementManager
-> MeasurementRepository
-> Room
```

Маршрут:

```text
RouteTargetManager / RoutePlanner
-> MainViewModel
-> MainUiState.routePolyline
-> MapObjectsController
-> MapOverlays route panel
```

Обновления:

```text
update.json
-> UpdateRepository / UpdateChecker
-> UpdateCoordinator
-> MainViewModel
-> UpdateBanner / MapOverlays
-> UpdateInstaller
-> системный установщик Android
```

## 5. Правила для карты

- Не возвращать глобальный `map.mapObjects.clear()` при обычном update карты.
- Объекты карты должны быть разделены на независимые группы:
  - `gpsObjects`;
  - `measurementObjects`;
  - `importedLocationObjects`;
  - `targetObjects`;
  - `routeObjects`.
- GPS-объекты обновляются отдельно от лучей, маршрутов, целей и импортированных точек.
- Изменение `satelliteCount` не должно пересоздавать лучи, маршруты и маркеры.
- `routePolyline` должен перерисовываться только при изменении маршрута.
- `measurements` должны перерисовываться только при изменении замеров или настроек их отображения.
- `importedLocations` должны перерисовываться отдельно от азимутных лучей.
- `target`, `intersection` и `destination` должны обновляться только при визуально значимых изменениях.
- Tap listeners на объектах MapKit должны сохраняться сильными ссылками, если этого требует MapKit.
- Цвета лучей должны вычисляться через актуальную модель: свой луч использует выбранный цвет пользователя, импортированный луч использует `colorArgb` или цвет импортированного луча по умолчанию.
- GPS-точка при активном маршруте отображается как стрелка по направлению маршрута.
- Не перекрывать логотипы, копирайты и служебные элементы Yandex MapKit.
- `MapObjectsController` остаётся владельцем низкоуровневой отрисовки MapKit-объектов.
- `YandexMapComposable` связывает Compose lifecycle с `MapView` и передаёт состояние в контроллер, а не дублирует отрисовку объектов.

TODO:

- `TargetObjectsKey` не должен зависеть от `subtitle` `RouteTarget`, чтобы изменение дистанции в `subtitle` не вызывало мерцание маркера пересечения.

## 6. Правила для MainViewModel

- `MainViewModel` — координатор UI-состояния, а не место всей бизнес-логики.
- Крупные подсистемы нельзя добавлять прямо в `MainViewModel`.
- Создание своего азимутного замера должно идти через `MeasurementManager`.
- Замер может создаваться из текущей GPS-точки или из произвольной точки карты. GPS-точность и спутники записываются только когда источник — текущая GPS-точка.
- Импорт `SECTOR_MEASUREMENT_V1` должен идти через `MeasurementManager` и `ExportFormat`.
- Импорт `SECTOR_LOCATION_V1` должен идти через `LocationShareManager` и `LocationExchangeFormat`.
- Экспорт одного или нескольких лучей должен идти через `MeasurementManager`.
- Выбор целей и ссылки на внешние маршруты должны идти через `RouteTargetManager`.
- Построение маршрута внутри приложения должно идти через `RoutePlanner`.
- Update-логика вынесена в `UpdateCoordinator` и должна оставаться там.
- `MainViewModel` может связывать UI с компонентами, но не должен снова становиться «бог-объектом».

## 7. Правила для MainScreen и Compose UI

- `MainScreen.kt` должен оставаться лёгким координатором экрана.
- Overlay-кнопки, GPS-индикатор, GPS-плашка, update banner и маршрутная панель живут в `ui/map/MapOverlays.kt`.
- Dialogs и bottom sheets живут отдельно. Сейчас основная точка сборки — `MainDialogHost` в `MainDialogs.kt`.
- `ImportDialog` принимает текст `SECTOR_MEASUREMENT_V1`, `SECTOR_LOCATION_V1` или текст с обоими форматами.
- `ExportMeasurementSelectionDialog` отвечает за выбор одного, нескольких или всех активных азимутных лучей для экспорта.
- Диалог ввода азимута должен поддерживать позывной, азимут, погрешность и мощность.
- Точка назначения по long tap открывает действия для маршрута, установки азимута, копирования координат и удаления точки.
- Маршрутные действия находятся рядом с выбранной точкой и маршрутной панелью, а не в отдельном пользовательском блоке настроек.
- Новые крупные composable не добавлять прямо в `MainScreen.kt`.
- UI-рефакторинг не должен менять поведение без отдельной задачи.
- Компоненты карты не должны напрямую менять состояние приложения в обход callbacks и `MainViewModel`.

## 8. Правила для обновлений

`update.json` содержит:

- `latestVersion`;
- `versionCode`;
- `apkUrl`;
- `changelog`;
- `mandatory`.

Правила:

- `update.json` лежит в `main`.
- `update.json` обновляется только после публикации GitHub Release и загрузки APK.
- `versionCode` в `update.json` должен быть больше установленного `versionCode`, иначе обновление не появится.
- APK скачивается во внутренний `cache` приложения.
- Установка запускается через `FileProvider` и системный установщик Android.
- Не использовать `file://` URI.
- `REQUEST_INSTALL_PACKAGES` нужен для установки APK вне Google Play.
- Если установка сорвалась, плашка обновления должна оставаться доступной.
- Если обновление доступно, плашка может появляться повторно при запуске или ручной проверке.
- Проверка доступности обновления должна оставаться отделённой от загрузки и установки.
- `UpdateRepository` читает и парсит `update.json`.
- `UpdateChecker` сравнивает доступную версию с установленной.
- `UpdateCoordinator` управляет состоянием проверки, баннером, прогрессом и ошибками.
- `UpdateInstaller` скачивает APK и запускает системную установку.

TODO:

- Добавить `sha256` в `update.json`.
- Проверять `sha256` скачанного APK.
- Проверять `packageName` скачанного APK.
- Проверять `versionCode` скачанного APK.
- Ограничить максимальный размер APK.
- Чистить старые APK из `cache`.

## 9. Правила для базы данных

- Room используется для локальных азимутных замеров и импортированных GPS-точек.
- DataStore используется для настроек и служебных флагов.
- Текущая Room schema version — `3`.
- `Measurement` хранит азимутные замеры.
- `Measurement.colorArgb` nullable и хранит цвет импортированного или экспортируемого луча.
- `ImportedLocation` хранит location-only точки без азимута.
- `ImportedLocationDao` отвечает за чтение, upsert и очистку импортированных GPS-точек.
- `ImportedLocationRepository` скрывает DAO от остального приложения.
- Миграция `1 -> 2` создаёт таблицу `imported_locations`.
- Миграция `2 -> 3` добавляет `measurements.color_argb`.
- Миграции должны быть non destructive.
- Изменение Room schema делать только отдельной задачей.
- Новые сущности вроде контактов, ключей и remote positions нельзя добавлять в `Measurement`.
- Для будущих контактов нужны отдельные таблицы и модели.
- Для будущих ключей нужны отдельные таблицы, модели или хранилища, выбранные по требованиям безопасности.
- `exportSchema=false` пока остаётся техническим долгом.

## 10. Форматы обмена

Подробно форматы описаны в [EXCHANGE_FORMATS.md](EXCHANGE_FORMATS.md).

- `SECTOR_MEASUREMENT_V1` описывает азимутный замер.
- Один текст может содержать несколько блоков `SECTOR_MEASUREMENT_V1`.
- `colorArgb` в `SECTOR_MEASUREMENT_V1` опциональный.
- Старые блоки без `colorArgb` должны импортироваться.
- Цвет импортированного луча должен сохраняться, если поле `colorArgb` валидно.
- Если часть measurement-блоков валидна, импортируются валидные, а битые пропускаются.
- Если все measurement-блоки битые, импорт завершается ошибкой.
- `SECTOR_LOCATION_V1` описывает GPS-точку без азимута.
- Location-only точки не включаются в экспорт азимутных лучей.
- Импорт и экспорт выполняются вручную через системный share, буфер обмена или вставку текста.

## 11. Безопасность и приватность

- Не хранить `local.properties`, `.jks`, `.keystore`, APK или AAB в git.
- Не логировать `MAPKIT_API_KEY`, signing secrets, пароли и другие секреты.
- Координаты и замеры не отправляются на сервер приложения автоматически.
- `SECTOR_LOCATION_V1` отправляется только вручную через системный share.
- Серверной синхронизации сейчас нет.
- Будущая передача координат должна быть только явной и управляемой пользователем.
- Будущая телеметрия, статистика или crash reporting требуют отдельного решения и явного согласования.
- Не использовать телефон, IMEI, Android ID или номер SIM как идентификатор.
- Для будущих контактов использовать случайный sector id и криптографические ключи.
- Приватные ключи хранить через Android Keystore.
- Сервер в будущем не должен видеть координаты в открытом виде.
- Любая серверная синхронизация координат должна проектироваться отдельно от UI-задач.

## 12. Будущие модули

Планируемые модули перечислены как архитектурные направления. В текущей версии они не реализованы.

- `contacts/`
  Локальные контакты, QR-обмен публичными ключами, fingerprints.

- `crypto/`
  Шифрование и подпись пакетов координат/азимута.

- `sync/`
  Серверная пересылка зашифрованных сообщений.

- `routes/`
  Альтернативные маршруты, время, дистанция, пробки/traffic info.

## 13. Backend телеметрии

Backend технической статистики находится в `backend/` и является отдельным модулем от Android-кода.

Состав:

- Go HTTP server на внутреннем `:8080`;
- SQLite база `/data/telemetry.db`;
- SQL migrations в `backend/migrations/`;
- Caddy reverse proxy для `https://telemetry.sector-map.ru`;
- Telegram daily report через существующего Telegram bot;
- JSON-only admin API без web UI.

Backend принимает только технические события `app_start`, `heartbeat` и `app_background`. Он не принимает координаты, азимуты, маршруты, замеры, позывной, контакты, IMEI, Android ID, телефон, SIM/operator, Google account или serial number.

Android-клиент телеметрии отделен от backend и будет реализован отдельной задачей. Текущая backend-задача не меняет `app/src`, Room schema, `versionName/versionCode` или `update.json`.

## 14. Что нельзя делать без отдельной задачи

- Менять package id.
- Менять release signing.
- Менять `update.json` в feature- или docs-задачах.
- Трогать `local.properties`.
- Добавлять APK, AAB или JKS в репозиторий.
- Добавлять Firebase, Analytics или Crashlytics.
- Добавлять серверную синхронизацию вместе с UI-правкой.
- Смешивать contacts, server, crypto и routes в одной задаче.
- Возвращать `map.mapObjects.clear()` в обычный update карты.
- Делать крупный рефакторинг вместе с функциональной правкой.
- Менять версию приложения без явного требования.
- Менять GitHub Actions без отдельной задачи.
- Форматировать весь проект ради локальной правки.
- Переносить существующую бизнес-логику в UI-компоненты.

## 15. Как работать с Codex

- Одна задача — одна feature/docs-ветка.
- Перед правкой проверять `git status --short --branch`.
- Если дерево не чистое — остановиться и запросить решение пользователя.
- Не делать commit или push без команды пользователя.
- Не открывать PR без команды пользователя.
- После правки обязательно сообщать изменённые файлы.
- Для документационных задач запускать `git diff --check`.
- Для кодовых задач запускать релевантные Gradle-проверки.
- Не трогать unrelated файлы.
- Не форматировать весь проект.
- Для документационных задач не менять код, версию, `update.json`, GitHub Actions и релизные артефакты.

## 16. Текущие технические долги

- `TargetObjectsKey` зависит от `subtitle` `RouteTarget`.
- `exportSchema=false` нужно заменить на экспортируемую схему Room.
- Нужна `sha256`-проверка обновлений.
- Нужны проверки `packageName` и `versionCode` скачанного APK.
- Нужны unit-тесты для ошибок `update.json`.
- Нужно продумать contacts, crypto и sync отдельно.
- Нужно добавить альтернативные маршруты отдельной задачей.
