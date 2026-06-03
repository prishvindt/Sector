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

Production backend поднят на VPS как отдельный Docker Compose project:

- домен приложения: `sector-map.ru`;
- telemetry endpoint: `https://telemetry.sector-map.ru`;
- VPS IP: `138.124.72.219`;
- Docker Compose project name: `sector-telemetry`;
- timezone отчета: `Europe/Moscow`;
- время ежедневного Telegram-отчета: `08:00`;
- SQLite база: `/opt/sector-telemetry/data/telemetry.db`.

Реальный порядок деплоя:

1. Подготовить каталог:

```bash
sudo mkdir -p /opt/sector-telemetry
sudo chown "$USER:$USER" /opt/sector-telemetry
cd /opt/sector-telemetry
```

2. Скопировать содержимое `backend/` в `/opt/sector-telemetry`: `Dockerfile`, `docker-compose.example.yml`, `Caddyfile.example`, `.env.example`, `cmd/`, `internal/`, `migrations/`, `go.mod` и `go.sum`.
3. Создать рабочие файлы из примеров:

```bash
cp .env.example .env
cp docker-compose.example.yml docker-compose.yml
cp Caddyfile.example Caddyfile
```

4. Заполнить `.env` реальными значениями `APP_TOKEN`, `ADMIN_TOKEN`, `TELEGRAM_BOT_TOKEN`, `TELEGRAM_CHAT_ID`, `TELEGRAM_REPORT_ENABLED=true`, `TELEGRAM_REPORT_TIME=08:00`, `TELEGRAM_REPORT_TIMEZONE=Europe/Moscow` и `PUBLIC_BASE_URL=https://telemetry.sector-map.ru`.
5. Если в рабочем `docker-compose.yml` используется отдельный `Caddyfile`, mount для Caddy должен указывать на него:

```yaml
./Caddyfile:/etc/caddy/Caddyfile:ro
```

6. Обязательно подготовить bind mount для SQLite под пользователя контейнера `uid 10001`:

```bash
sudo mkdir -p /opt/sector-telemetry/data
sudo chown -R 10001:10001 /opt/sector-telemetry/data
sudo chmod 750 /opt/sector-telemetry/data
```

Контейнер backend запускается не от `root`, а от пользователя `sector` с `uid 10001`. Без этого шага первый запуск может упасть с ошибкой:

```text
open store: ping sqlite: unable to open database file: no such file or directory
```

После исправления прав backend должен стартовать с логом:

```text
sector-telemetry: listening on :8080
```

7. Запустить:

```bash
docker compose -p sector-telemetry up -d --build
```

Compose не публикует порт backend наружу. Снаружи открыты только порты Caddy `80` и `443`.

Проверка контейнеров, логов и HTTPS:

```bash
docker compose -p sector-telemetry ps
docker compose -p sector-telemetry logs --tail=100
curl -i https://telemetry.sector-map.ru/health
```

`/health` должен отвечать:

```json
{"ok":true,"service":"sector-telemetry","version":"0.1.0"}
```

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
- `TELEGRAM_REPORT_TIMEZONE` - timezone отчета, например `Europe/Moscow`.
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

Пример `app_start`:

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

Пример `heartbeat`:

```bash
curl -X POST https://telemetry.sector-map.ru/api/v1/events \
  -H 'Content-Type: application/json' \
  -H "X-App-Token: $APP_TOKEN" \
  -d '{
    "installId": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "heartbeat",
    "appVersion": "0.1.8",
    "versionCode": 9,
    "sessionId": "11111111-1111-4111-8111-111111111111"
  }'
```

Пример `app_background`:

```bash
curl -X POST https://telemetry.sector-map.ru/api/v1/events \
  -H 'Content-Type: application/json' \
  -H "X-App-Token: $APP_TOKEN" \
  -d '{
    "installId": "550e8400-e29b-41d4-a716-446655440000",
    "eventType": "app_background",
    "appVersion": "0.1.8",
    "versionCode": 9,
    "sessionId": "11111111-1111-4111-8111-111111111111",
    "sessionDurationSeconds": 180
  }'
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

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/devices \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

```bash
curl 'https://telemetry.sector-map.ru/api/v1/admin/events?limit=100' \
  -H "X-Admin-Token: $ADMIN_TOKEN"
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

Быстрая проверка bot token/chat id через прямой `sendMessage`:

```bash
curl -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  -d "chat_id=${TELEGRAM_CHAT_ID}" \
  --data-urlencode "text=sector telemetry test"
```

На production ручной тест Telegram прошел: сообщение пришло в группу Monitor.

## Обслуживание

Основные команды выполняются из `/opt/sector-telemetry`:

```bash
docker compose -p sector-telemetry ps
docker compose -p sector-telemetry logs --tail=100
docker compose -p sector-telemetry restart sector-telemetry
docker compose -p sector-telemetry down
docker compose -p sector-telemetry up -d
```

## Backup SQLite

Данные лежат в bind mount:

```text
/opt/sector-telemetry/data/telemetry.db
```

`data/` принадлежит `uid 10001`, потому что backend-контейнер работает не от `root`. Обычный deploy-user может не иметь доступа к `telemetry.db`, и это нормально. Backup/restore выполнять через `sudo`.

Перед backup backend-контейнер лучше остановить, чтобы SQLite database и WAL-файлы были в консистентном состоянии:

```bash
cd /opt/sector-telemetry
docker compose -p sector-telemetry stop sector-telemetry
sudo tar -czf "/opt/sector-telemetry/backup-$(date +%F-%H%M).tar.gz" -C /opt/sector-telemetry data
docker compose -p sector-telemetry start sector-telemetry
sudo ls -lh /opt/sector-telemetry/backup-*.tar.gz
```

Файлы `telemetry.db-shm` и `telemetry.db-wal` рядом с базой нормальны для SQLite WAL mode. Не удалять их вручную при работающем контейнере.

## Restore SQLite

Restore тоже выполнять с остановленным backend. Проще остановить весь compose project, заменить каталог `data/` из backup-архива и вернуть владельца/права для `uid 10001`:

```bash
cd /opt/sector-telemetry
docker compose -p sector-telemetry down
sudo rm -rf /opt/sector-telemetry/data
sudo tar -xzf /opt/sector-telemetry/backup-YYYY-MM-DD-HHMM.tar.gz -C /opt/sector-telemetry
sudo chown -R 10001:10001 /opt/sector-telemetry/data
sudo chmod 750 /opt/sector-telemetry/data
docker compose -p sector-telemetry up -d
curl -i https://telemetry.sector-map.ru/health
```

После restore проверить admin summary:

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/summary \
  -H "X-Admin-Token: $ADMIN_TOKEN"
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
