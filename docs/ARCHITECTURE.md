# Архитектура проекта «Сектор»

## 1. Назначение проекта

«Сектор» — Android-приложение для работы с GPS-точкой, азимутами, секторами погрешности, замерами, маршрутами и обновлениями вне Google Play.

Приложение использует Yandex MapKit для карты, отображения объектов и построения маршрутов.

Текущая архитектура локальная: координаты пользователя не отправляются на сервер приложения. Внешние сетевые обращения сейчас относятся к Yandex MapKit, проверке `update.json` и загрузке APK обновления.

## 2. Ветки и релизная модель

- `develop` — ветка разработки и тестирования.
- `main` — стабильная релизная ветка.
- `feature/*` или другие feature-ветки — временные ветки для отдельных задач.
- Релиз делается только из `main`.
- `main` считается стабильной версией.
- `develop` считается тестируемой следующей версией.
- `develop` нельзя автоматически мержить в `main` без ручной проверки.
- `update.json` обновляется только после публикации GitHub Release с APK.
- `versionCode` всегда должен увеличиваться.
- `versionName` должен соответствовать имени APK и релизу.
- Имя APK формируется из `versionName`, поэтому релиз, APK и `update.json` должны описывать одну и ту же версию.

## 3. Структура пакетов

Базовый путь исходников:

```text
app/src/main/java/com/prishvindt/sector/
```

- `data/`
  Room, DataStore, репозитории, локальные настройки и замеры. Основные классы: `AppDatabase`, `Measurement`, `MeasurementDao`, `MeasurementRepository`, `SettingsRepository`.

- `domain/`
  Чистая доменная логика: геометрия, расчёт сектора, пересечения, импорт/экспорт. Основные классы: `GeoMath`, `SectorCalculator`, `BearingIntersection`, `IntersectionTargetCalculator`, `ExportFormat`, `MeasurementMerge`, `RouteTarget`.

- `domain/measurements/`
  Сценарии работы с замерами поверх репозитория. Основной класс: `MeasurementManager`.

- `domain/routes/`
  Доменная логика целей маршрута и внешних ссылок. Основной класс: `RouteTargetManager`.

- `location/`
  GPS, GNSS-спутники, активный поиск и foreground service. Основные классы: `LocationTracker`, `GnssSatelliteTracker`, `LocationState`, `ActiveSearchService`.

- `map/`
  Yandex MapKit, отрисовка объектов карты, контроллер объектов карты, обработка тапов и построение маршрутов. Основные классы: `YandexMapComposable`, `MapObjectsController`, `MapTapHandler`, `MapStyle`, `RoutePlanner`.

- `ui/`
  Compose UI, `MainScreen`, `MainViewModel`, drawer, settings, about, dialogs, overlays. Подпакеты: `about/`, `callsign/`, `common/`, `drawer/`, `firststart/`, `importdata/`, `input/`, `map/`, `measurements/`, `settings/`.

- `updates/`
  Проверка `update.json`, состояние обновления, загрузка APK, запуск системного установщика. Основные классы: `UpdateRepository`, `UpdateChecker`, `UpdateCoordinator`, `UpdateInstaller`, `UpdateInfo`, `UpdateStatus`.

- `service/`
  Отдельные Android service-компоненты вне GPS-пакета. Сейчас содержит `ExternalActionService`.

Корневые классы:

- `SectorApplication` — инициализация MapKit и контейнера зависимостей.
- `MainActivity` — Android entry point и связывание Activity с Compose.

## 4. Потоки данных

GPS:

```text
LocationTracker / ActiveSearchService
-> MainViewModel
-> MainUiState
-> MainScreen / MapOverlays
-> YandexMapComposable / MapObjectsController
```

Замеры:

```text
Room / MeasurementDao
-> MeasurementRepository
-> MeasurementManager / MainViewModel
-> MainUiState
-> карта / список / экспорт
```

Маршрут:

```text
RoutePlanner
-> MainViewModel
-> MainUiState.routePolyline
-> MapObjectsController
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

- Не возвращать глобальный `map.mapObjects.clear()` при каждом update карты.
- Объекты карты должны быть разделены на независимые группы/коллекции:
  - `gpsObjects`;
  - `measurementObjects`;
  - `routeObjects`;
  - `targetObjects`.
- Изменение `satelliteCount` не должно пересоздавать лучи, секторы, маршруты и маркеры.
- GPS-объекты обновлять отдельно от маршрутов и замеров.
- `routePolyline` должен перерисовываться только при изменении маршрута.
- `measurements` должны перерисовываться только при изменении замеров или настроек их отображения.
- `target`, `intersection`, `destination` должны обновляться только при визуально значимых изменениях.
- Не перекрывать логотипы, копирайты и служебные элементы Yandex MapKit.
- Tap listeners на маркерах должны сохранять актуальные данные.
- `MapObjectsController` должен оставаться владельцем низкоуровневой отрисовки MapKit-объектов.
- `YandexMapComposable` должен связывать Compose lifecycle с `MapView` и передавать состояние в контроллер, а не дублировать отрисовку объектов.

TODO:

- `TargetObjectsKey` не должен зависеть от `subtitle` `RouteTarget`, чтобы изменение дистанции в `subtitle` не вызывало мерцание маркера пересечения.

## 6. Правила для MainViewModel

- `MainViewModel` — координатор UI-состояния, а не место всей бизнес-логики.
- Крупные подсистемы нельзя добавлять прямо в `MainViewModel`.
- Update-логика вынесена в `UpdateCoordinator` и должна оставаться там.
- Работа с замерами должна идти через `MeasurementManager` и `MeasurementRepository`.
- Построение маршрутов должно идти через `RoutePlanner` и доменные helpers из `domain/routes/`.
- Будущие `route`, `gps`, `contacts`, `sync`, `crypto` подсистемы нужно выносить в отдельные компоненты.
- `MainViewModel` может связывать UI с компонентами, но не должен снова становиться “бог-объектом”.

## 7. Правила для MainScreen и Compose UI

- `MainScreen.kt` должен оставаться лёгким координатором экрана.
- Overlay-кнопки, GPS-индикатор, GPS-плашка и update banner живут в отдельном overlay-компоненте: сейчас это `ui/map/MapOverlays.kt`.
- Dialogs и bottom sheets живут отдельно: сейчас основная точка сборки — `MainDialogHost` в `MainDialogs.kt`, отдельные экраны и диалоги вынесены в подпакеты `ui/`.
- Новые крупные composable не добавлять прямо в `MainScreen.kt`.
- UI-рефакторинг не должен менять поведение без отдельной задачи.
- Настройки должны оставаться в `SettingsScreen` и связанных моделях, а не расползаться по `MainScreen`.
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

- Room используется для локальных замеров.
- DataStore используется для настроек и служебных флагов.
- Текущая Room-сущность `Measurement` должна оставаться сущностью замера.
- Новые сущности вроде контактов, ключей, remote positions нельзя добавлять в `Measurement`.
- Для будущих контактов нужны отдельные таблицы и модели.
- Для будущих ключей нужны отдельные таблицы, модели или хранилища, выбранные по требованиям безопасности.
- Перед изменением схемы Room надо продумать миграции.
- Нельзя менять схему базы вместе с unrelated UI-правкой.

TODO:

- Включить `exportSchema`.
- Подготовить миграции Room перед расширением базы.

## 10. Безопасность и приватность

- Не хранить `local.properties`, `.jks`, `.keystore`, APK или AAB в git.
- Не логировать `MAPKIT_API_KEY`, signing secrets, пароли и другие секреты.
- Координаты пользователя не отправляются на сервер приложения в текущей архитектуре.
- Будущая передача координат должна быть только явной и управляемой пользователем.
- Не использовать телефон, IMEI, Android ID или номер SIM как идентификатор контакта.
- Для будущих контактов использовать случайный sector id и криптографические ключи.
- Приватные ключи хранить через Android Keystore.
- Сервер в будущем не должен видеть координаты в открытом виде.
- Любая серверная синхронизация координат должна проектироваться отдельно от UI-задач.
- Любая новая аналитика, телеметрия или crash reporting требует отдельного решения и явного согласования.

## 11. Будущие модули

Планируемые модули перечислены как архитектурные направления. В этой документационной задаче они не реализуются.

- `contacts/`
  Локальные контакты, QR-обмен публичными ключами, fingerprints.

- `crypto/`
  Шифрование и подпись пакетов координат/азимута.

- `sync/`
  Серверная пересылка зашифрованных сообщений.

- `routes/`
  Альтернативные маршруты, время, дистанция, пробки/traffic info.

## 12. Что нельзя делать без отдельной задачи

- Менять package id.
- Менять release signing.
- Менять `update.json` в feature-задачах.
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

## 13. Как работать с Codex

- Одна задача — одна feature-ветка.
- Перед правкой проверять `git status`.
- Если дерево не чистое — остановиться и запросить решение пользователя.
- Не делать commit или push без команды пользователя.
- Не открывать PR без команды пользователя.
- После правки обязательно сообщать изменённые файлы.
- После правки запускать `./gradlew :app:assembleDebug` и `git diff --check`.
- При возможности запускать `./gradlew :app:testDebugUnitTest`.
- Не трогать unrelated файлы.
- Не форматировать весь проект.
- Для документационных задач не менять код, версию, `update.json` и релизные файлы.

## 14. Текущие технические долги

- `TargetObjectsKey` зависит от `subtitle` `RouteTarget`.
- Нужна `sha256`-проверка обновлений.
- Нужны проверки `packageName` и `versionCode` скачанного APK.
- Нужна документация `RELEASE.md`.
- Нужно обновить `README.md`.
- Нужны тесты update flow.
- Нужны unit-тесты для ошибок `update.json`.
- Нужно улучшить Room migrations и `exportSchema`.
- Нужно продумать contacts, crypto и sync отдельно.
- Нужно добавить альтернативные маршруты отдельной задачей.
