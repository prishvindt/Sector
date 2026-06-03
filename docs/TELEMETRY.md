# Телеметрия

Документ описывает техническую статистику приложения "Сектор": Android-клиент, публичный endpoint и privacy-границы.

## Назначение

Техническая статистика нужна для минимального понимания, что официальная сборка запускается, остается открытой и корректно завершает foreground-сессии. Это не серверная синхронизация данных пользователя и не аналитика поведения на карте.

Официальный backend работает на:

```text
https://telemetry.sector-map.ru
```

Android-клиент отправляет события на:

```text
POST /api/v1/events
```

Headers:

```text
Content-Type: application/json
X-App-Token: ...
```

`TELEMETRY_APP_TOKEN` находится внутри APK и не считается секретом. Это фильтр от случайного мусора; реальный токен не хранится в git и задается только при сборке.

## Конфигурация Android-сборки

Клиент читает:

- `TELEMETRY_URL`;
- `TELEMETRY_APP_TOKEN`.

Значения можно задать в `local.properties` или как Gradle property:

```properties
TELEMETRY_URL=https://telemetry.sector-map.ru
TELEMETRY_APP_TOKEN=...
```

Если `TELEMETRY_URL` или `TELEMETRY_APP_TOKEN` пустые, телеметрия недоступна, выключена в UI и не отправляет запросы.

`local.properties` не коммитится. Реальный `TELEMETRY_APP_TOKEN` не добавляется в git.

## Поведение по умолчанию

Если сборка содержит непустые `TELEMETRY_URL` и `TELEMETRY_APP_TOKEN`, техническая статистика включена по умолчанию.

Пользователь может выключить ее в настройках:

```text
Настройки -> Техническая статистика -> Техническая статистика
```

Отдельное окно согласия не показывается. Под переключателем нет длинного поясняющего текста. В том же блоке есть кнопка "Сбросить ID статистики": она создает новый случайный `installId`; старый id на сервере не удаляется.

Настройки хранятся в DataStore:

- `telemetry_enabled`;
- `telemetry_install_id`.

`installId` - случайный UUID установки. Android ID, IMEI, номер телефона, SIM, оператор и аккаунты не используются.

## События

### `app_start`

Отправляется при переходе приложения в foreground. На каждую foreground-сессию создается новый `sessionId`.

Payload:

```json
{
  "installId": "...",
  "eventType": "app_start",
  "appVersion": "0.1.7",
  "versionCode": 8,
  "manufacturer": "samsung",
  "model": "SM-G991B",
  "androidSdk": 35,
  "sessionId": "..."
}
```

### `heartbeat`

Отправляется примерно каждые 15 минут только пока приложение находится в foreground. Когда приложение уходит в background, heartbeat прекращается. Foreground service, background worker и фоновая статистика не используются.

### `app_background`

Отправляется при уходе приложения в background. Payload содержит длительность foreground-сессии:

```json
{
  "installId": "...",
  "eventType": "app_background",
  "appVersion": "0.1.7",
  "versionCode": 8,
  "manufacturer": "samsung",
  "model": "SM-G991B",
  "androidSdk": 35,
  "sessionId": "...",
  "sessionDurationSeconds": 5400
}
```

Если `app_background` не успел отправиться, это допустимо: Android мог остановить процесс.

## Что отправляется

- случайный `installId`;
- `eventType`: `app_start`, `heartbeat`, `app_background`;
- `appVersion`;
- `versionCode`;
- manufacturer;
- model;
- android sdk;
- `sessionId`;
- `sessionDurationSeconds` только для `app_background`.

## Что не отправляется

Клиент гарантированно не добавляет в payload:

- координаты;
- маршруты;
- азимуты;
- замеры;
- позывной;
- контакты;
- Android ID;
- IMEI;
- номер телефона;
- SIM/operator;
- Google account;
- serial number;
- токен телеметрии в body или logs.

Backend также сохраняет только allowlisted поля событий. Лишние JSON-поля на events endpoint игнорируются и не сохраняются.

## Реализация Android-клиента

Код разделен по пакетам:

- `domain/telemetry/` - модели событий, payload, настройки, репозиторий и session/heartbeat-логика;
- `telemetry/TelemetryHttpClient` - отправка `POST /api/v1/events` через `HttpURLConnection`;
- `lifecycle/TelemetryLifecycleObserver` - связь с lifecycle приложения;
- `data/SettingsRepository` - DataStore-ключи `telemetry_enabled` и `telemetry_install_id`;
- `ui/settings/SettingsScreen` - блок "Техническая статистика".

Lifecycle реализован через `ProcessLifecycleOwner`, поэтому добавлена зависимость `androidx.lifecycle:lifecycle-process`. Она нужна только для process-level foreground/background callbacks и не создает service, worker или постоянное уведомление.

Сетевые запросы выполняются не на main thread. Таймауты короткие: connect 5 секунд и read 5 секунд. Ошибки сети не роняют приложение.

## Backend

Backend находится в `backend/` и является отдельным модулем от Android-кода. Сервер сам не опрашивает приложение; все события приходят только по инициативе Android-клиента.

Admin API возвращает JSON:

- `GET /api/v1/admin/summary`;
- `GET /api/v1/admin/devices`;
- `GET /api/v1/admin/events`.

Доступ к admin API требует `X-Admin-Token`. Telegram daily report формируется backend-сервером отдельно от Android-клиента.
