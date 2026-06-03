# Sector telemetry backend

Публичный backend технической статистики приложения «Сектор».

Backend принимает только минимальные события `app_start`, `heartbeat` и `app_background`, хранит агрегированную статистику устройств в SQLite и может отправлять ежедневный Telegram-отчет. Android-клиент телеметрии в этой задаче не реализован.

## Структура

```text
backend/
  cmd/server/              HTTP entry point
  internal/config/         env-конфигурация
  internal/http/           HTTP handlers и token checks
  internal/security/       in-memory rate limit
  internal/store/          SQLite store, migrations, admin queries
  internal/telemetry/      validation и allowlist payload
  internal/reporter/       Telegram daily report
  migrations/              SQL migrations
```

## Локальный запуск

Нужен Go и SQLite driver dependency из `go.mod`.

```bash
cd backend
cp .env.example .env
```

Для локальной разработки можно временно указать:

```env
DATABASE_PATH=./data/telemetry.db
MIGRATIONS_DIR=./migrations
APP_TOKEN=local-app-token
ADMIN_TOKEN=local-admin-token
```

Запуск:

```bash
go run ./cmd/server
```

Проверка:

```bash
curl http://localhost:8080/health
```

## Запуск на VPS

1. Скопировать содержимое `backend/` в `/opt/sector-telemetry`.
2. Скопировать `.env.example` в `.env` и заполнить реальные значения.
3. Проверить `Caddyfile.example` и домен `telemetry.sector-map.ru`.
4. Запустить:

```bash
docker compose -f docker-compose.example.yml up -d
```

Compose не публикует порт backend наружу. Снаружи открыты только порты Caddy `80` и `443`.

## Env

- `APP_ENV` - `production` по умолчанию.
- `HTTP_ADDR` - внутренний адрес HTTP server, по умолчанию `:8080`.
- `DATABASE_PATH` - путь к SQLite базе, по умолчанию `/data/telemetry.db`.
- `MIGRATIONS_DIR` - путь к SQL migrations, в контейнере `/app/migrations`.
- `APP_TOKEN` - публичный фильтр для `/api/v1/events`. Он попадет в APK, поэтому это не секрет.
- `ALLOW_EMPTY_APP_TOKEN` - только для dev mode. В production оставлять `false`.
- `ADMIN_TOKEN` - токен закрытого admin API.
- `EVENT_RETENTION_DAYS` - сколько дней хранить raw events, по умолчанию `180`.
- `TELEGRAM_REPORT_ENABLED` - включает ежедневный отчет.
- `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID` - настройки существующего Telegram bot.
- `TELEGRAM_REPORT_TIME` - локальное время отчета, например `08:00`.
- `TELEGRAM_REPORT_TIMEZONE` - timezone отчета, например `Europe/Tallinn`.
- `TELEGRAM_MAX_DEVICES_IN_REPORT` - максимум устройств в подробной части отчета.
- `PUBLIC_BASE_URL` - публичный адрес backend.

Настоящий `.env` не хранится в git.

## Endpoints

### `GET /health`

Без токена.

```json
{
  "ok": true,
  "service": "sector-telemetry",
  "version": "0.1.0"
}
```

### `POST /api/v1/events`

Требует header:

```text
X-App-Token: change-me-public-filter-token
```

Пример:

```bash
curl -X POST https://telemetry.sector-map.ru/api/v1/events \
  -H 'Content-Type: application/json' \
  -H 'X-App-Token: change-me-public-filter-token' \
  -d '{
    "installId": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "app_start",
    "appVersion": "0.1.8",
    "versionCode": 9,
    "manufacturer": "samsung",
    "model": "SM-G991B",
    "androidSdk": 35
  }'
```

Успех:

```json
{"ok": true}
```

Поддерживаемые `eventType`: `app_start`, `heartbeat`, `app_background`.

Для `heartbeat` можно передать `sessionId`. Для `app_background` можно передать `sessionId` и `sessionDurationSeconds`.

Лишние JSON-поля игнорируются, но не сохраняются. Сохраняются только allowlisted technical fields.

### Admin API

Все admin endpoints требуют:

```text
X-Admin-Token: change-me-admin-secret
```

Если `ADMIN_TOKEN` пустой, admin API возвращает `503` и не открывается без токена.

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/summary \
  -H 'X-Admin-Token: change-me-admin-secret'
```

Endpoints:

- `GET /api/v1/admin/summary`
- `GET /api/v1/admin/devices`
- `GET /api/v1/admin/events?limit=100&uid=uid:001&since=2026-07-04T00:00:00Z`

## SQLite schema

`devices` хранит агрегированную запись устройства:

- server-generated `uid_number`, показывается как `uid:001`;
- `install_id`;
- first/last seen timestamps;
- app version/version code;
- manufacturer/model/android sdk;
- счетчики запусков, heartbeat и суммарное время сессий.

`events` хранит raw technical events:

- device id;
- event type;
- app version/version code;
- optional session id;
- optional session duration;
- created timestamp.

SQL migrations лежат в `migrations/` и применяются при старте через `schema_migrations`.

## Логика событий

- `app_start`: создает device при новом `installId`, обновляет device info, увеличивает `launch_count`, пишет event.
- `heartbeat`: обновляет `last_seen_at`, увеличивает `heartbeat_count`, пишет event.
- `app_background`: обновляет `last_seen_at`, добавляет положительный `sessionDurationSeconds` в `total_session_seconds`, пишет event.

Если `app_background` не пришел, это допустимо: Android мог быть остановлен системой.

## Telegram report

Если `TELEGRAM_REPORT_ENABLED=true` и заполнены `TELEGRAM_BOT_TOKEN`/`TELEGRAM_CHAT_ID`, backend запускает scheduler в отдельной goroutine и отправляет ежедневный отчет в `TELEGRAM_REPORT_TIME` с учетом `TELEGRAM_REPORT_TIMEZONE`.

Если Telegram token/chat id не заданы, backend логирует предупреждение и продолжает работать без Telegram.

Длинные отчеты разбиваются на несколько сообщений, чтобы не превышать лимит Telegram.

## Backup SQLite

В контейнере:

```bash
docker compose -f docker-compose.example.yml exec sector-telemetry \
  sh -c 'cp /data/telemetry.db /data/telemetry-backup.db'
```

Лучше делать backup при остановленном сервисе или через `sqlite3 .backup`, если `sqlite3` установлен на хосте:

```bash
sqlite3 ./data/telemetry.db ".backup ./data/telemetry-$(date +%F).db"
```

## Restore SQLite

1. Остановить сервис:

```bash
docker compose -f docker-compose.example.yml stop sector-telemetry
```

2. Заменить `./data/telemetry.db` backup-файлом.
3. Запустить сервис:

```bash
docker compose -f docker-compose.example.yml up -d
```

## Безопасность и приватность

- `/api/v1/events` принимает только `POST`.
- Требуется `Content-Type: application/json`.
- Body limit: `8 KB`.
- Есть in-memory rate limit по IP и `installId`.
- `APP_TOKEN` не считается секретом, это только фильтр от случайного мусора.
- `ADMIN_TOKEN` обязателен для admin API.
- Backend не логирует полный body, `APP_TOKEN` или `ADMIN_TOKEN`.
- Raw events удаляются по retention, агрегированные `devices` хранятся бессрочно.

Backend не принимает и не хранит координаты, азимуты, маршруты, замеры, позывной, контакты, IMEI, Android ID, телефон, SIM/operator, Google account или serial number.
