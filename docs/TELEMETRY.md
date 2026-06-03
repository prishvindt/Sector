# Телеметрия

Документ описывает backend технической статистики. Android-клиент отправки телеметрии будет отдельной задачей и не входит в текущую реализацию.

## Архитектура

Официальный backend планируется на `https://telemetry.sector-map.ru`.

Схема:

```text
Android client с явным согласием пользователя
-> POST /api/v1/events
-> Go backend
-> SQLite /data/telemetry.db
-> admin JSON API и Telegram daily report
```

Reverse proxy - Caddy. Backend работает на внутреннем `:8080` и не публикуется наружу напрямую. На VPS открыты только `80/443` Caddy.

Self-hosted сборки могут поднять свой backend и указать свой `TELEMETRY_URL` в будущей Android-задаче.

## Какие данные собираются

Только техническая статистика:

- случайный `installId`, созданный клиентом;
- `eventType`: `app_start`, `heartbeat`, `app_background`;
- `appVersion`;
- `versionCode`;
- manufacturer;
- model;
- android sdk;
- optional `sessionId`;
- optional `sessionDurationSeconds`.

Сервер также создает свой публичный `uid_number`, который показывается как `uid:001`, `uid:002` и так далее. Это не секрет.

## Какие данные не собираются

Backend не принимает и не хранит:

- координаты;
- азимуты;
- маршруты;
- замеры;
- позывной;
- контакты;
- IMEI;
- Android ID;
- номер телефона;
- SIM/operator;
- Google account;
- serial number.

Лишние JSON-поля на events endpoint игнорируются и не сохраняются. В базе есть только allowlisted поля.

## События

### `app_start`

Создает device, если `installId` новый. Обновляет `last_seen_at`, app version/version code и device info. Увеличивает `launch_count` и записывает event.

### `heartbeat`

Обновляет `last_seen_at`, увеличивает `heartbeat_count`, записывает event. `launch_count` не увеличивается.

### `app_background`

Обновляет `last_seen_at`, записывает event. Если `sessionDurationSeconds > 0`, добавляет длительность к `total_session_seconds`.

Если `app_start` был, а `app_background` не пришел, это нормальная ситуация: Android мог быть остановлен системой.

## Public API

`POST /api/v1/events` принимает JSON до `8 KB` и требует `X-App-Token`.

`APP_TOKEN` будет находиться в APK, поэтому он не является секретом. Это фильтр от случайного мусора. Если `APP_TOKEN` пустой, production default - не принимать events. Для локальной разработки можно включить `ALLOW_EMPTY_APP_TOKEN=true`.

## Admin API

Admin API возвращает только JSON:

- `GET /api/v1/admin/summary`
- `GET /api/v1/admin/devices`
- `GET /api/v1/admin/events`

Доступ только с `X-Admin-Token`. Если `ADMIN_TOKEN` не задан, admin API выключен и возвращает `503`.

`/api/v1/admin/events` поддерживает `limit`, `uid` и `since` в RFC3339.

## Telegram daily report

Backend может отправлять ежедневный отчет через существующего Telegram bot.

Env:

- `TELEGRAM_REPORT_ENABLED`;
- `TELEGRAM_BOT_TOKEN`;
- `TELEGRAM_CHAT_ID`;
- `TELEGRAM_REPORT_TIME`;
- `TELEGRAM_REPORT_TIMEZONE`;
- `TELEGRAM_MAX_DEVICES_IN_REPORT`.

Если token/chat id не заданы, backend продолжает работать без Telegram и логирует предупреждение.

Отчет содержит:

- период;
- сводку по устройствам и событиям;
- версии;
- последние устройства;
- топ моделей;
- устройства на старых версиях.

Длинный отчет разбивается на несколько сообщений.

## Privacy notes

Телеметрия должна отправляться только после явного согласия пользователя в Android-клиенте.

Текущая задача реализует только backend. Она не добавляет Android telemetry client, не меняет `app/src`, не меняет `versionName/versionCode` и не меняет `update.json`.
