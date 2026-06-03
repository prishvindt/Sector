# Self-hosting telemetry backend

Инструкция для запуска backend технической статистики на VPS Debian/Ubuntu.

Production deployment уже выполнен для `sector-map.ru`:

- telemetry endpoint: `https://telemetry.sector-map.ru`;
- VPS IP: `138.124.72.219`;
- Docker Compose project name: `sector-telemetry`;
- Caddy получил HTTPS-сертификаты для `sector-map.ru`, `www.sector-map.ru` и `telemetry.sector-map.ru`;
- `/health` отвечает `{"ok":true,"service":"sector-telemetry","version":"0.1.0"}`;
- `POST /api/v1/events`, `/api/v1/admin/summary`, `/api/v1/admin/devices` и `/api/v1/admin/events` проверены через `curl`;
- Telegram test прошел, сообщение пришло в группу Monitor;
- SQLite база создана в `/opt/sector-telemetry/data`.

## 1. Домен и DNS

Для production настроены A-записи:

```text
A sector-map.ru -> 138.124.72.219
A www.sector-map.ru -> 138.124.72.219
A telemetry.sector-map.ru -> 138.124.72.219
```

Проверка DNS:

```bash
nslookup sector-map.ru
nslookup www.sector-map.ru
nslookup telemetry.sector-map.ru
```

Если установлен `dig`:

```bash
dig +short sector-map.ru A
dig +short www.sector-map.ru A
dig +short telemetry.sector-map.ru A
```

`sector-map.ru` должен указывать на актуальный VPS IP. Если у домена осталось две A-записи, удалить лишнюю у DNS-провайдера.

## 2. Установить Docker

На Debian/Ubuntu установить Docker Engine и Docker Compose plugin по инструкции Docker.

Проверить:

```bash
docker --version
docker compose version
```

## 3. Подготовить каталог

```bash
sudo mkdir -p /opt/sector-telemetry
sudo chown "$USER:$USER" /opt/sector-telemetry
cd /opt/sector-telemetry
```

## 4. Получить репозиторий и скопировать backend

Можно клонировать репозиторий во временный каталог и скопировать только `backend/`:

```bash
git clone <repo-url> /tmp/sector
cp -a /tmp/sector/backend/. /opt/sector-telemetry/
cd /opt/sector-telemetry
```

В `/opt/sector-telemetry` должны оказаться backend-файлы:

```text
Dockerfile
docker-compose.example.yml
Caddyfile.example
.env.example
cmd/
internal/
migrations/
go.mod
go.sum
```

## 5. Создать рабочие файлы

```bash
cp .env.example .env
cp docker-compose.example.yml docker-compose.yml
cp Caddyfile.example Caddyfile
```

Если используется рабочий `Caddyfile`, в `docker-compose.yml` mount Caddy должен указывать на него:

```yaml
./Caddyfile:/etc/caddy/Caddyfile:ro
```

## 6. Настроить `.env`

Сгенерировать токены:

```bash
openssl rand -hex 32
openssl rand -hex 32
```

Первое значение можно использовать как `APP_TOKEN`, второе - как `ADMIN_TOKEN`.

Минимальные production-настройки:

```env
APP_ENV=production
HTTP_ADDR=:8080
DATABASE_PATH=/data/telemetry.db
MIGRATIONS_DIR=/app/migrations

APP_TOKEN=<openssl-random-hex>
ALLOW_EMPTY_APP_TOKEN=false
ADMIN_TOKEN=<openssl-random-hex>

TELEGRAM_REPORT_ENABLED=true
TELEGRAM_BOT_TOKEN=<telegram-bot-token>
TELEGRAM_CHAT_ID=<telegram-chat-id>
TELEGRAM_REPORT_TIME=08:00
TELEGRAM_REPORT_TIMEZONE=Europe/Moscow
TELEGRAM_MAX_DEVICES_IN_REPORT=20

PUBLIC_BASE_URL=https://telemetry.sector-map.ru
```

`TELEGRAM_BOT_TOKEN` копируется целиком, вместе с двоеточием, например в формате `1234567890:AA...`.

`TELEGRAM_CHAT_ID` можно взять одним из способов:

- через `getUpdates`: добавить бота в группу, отправить сообщение в группу и выполнить `curl "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/getUpdates"`;
- из ссылки web Telegram вида `https://web.telegram.org/a/#-100...`; значение chat id начинается с `-100`.

Настоящий `.env` не хранится в git и не публикуется.

## 7. Проверить Telegram вручную

Перед запуском backend удобно проверить bot token/chat id напрямую:

```bash
curl -X POST "https://api.telegram.org/bot${TELEGRAM_BOT_TOKEN}/sendMessage" \
  -d "chat_id=${TELEGRAM_CHAT_ID}" \
  --data-urlencode "text=sector telemetry test"
```

## 8. Подготовить `data/` под UID 10001

Backend-контейнер запускается не от `root`, а от пользователя `sector` с `uid 10001`. Поэтому bind mount для SQLite должен принадлежать этому UID до первого запуска:

```bash
sudo mkdir -p /opt/sector-telemetry/data
sudo chown -R 10001:10001 /opt/sector-telemetry/data
sudo chmod 750 /opt/sector-telemetry/data
```

Это обязательный шаг. Без него backend может упасть с ошибкой:

```text
open store: ping sqlite: unable to open database file: no such file or directory
```

## 9. Запустить

```bash
cd /opt/sector-telemetry
docker compose -p sector-telemetry config
docker compose -p sector-telemetry up -d --build
```

Проверить контейнеры и логи:

```bash
docker compose -p sector-telemetry ps
docker compose -p sector-telemetry logs --tail=100
```

В логах backend ожидается:

```text
sector-telemetry: listening on :8080
```

## 10. Проверить `/health`

```bash
curl -i https://telemetry.sector-map.ru/health
```

Ожидается:

```json
{"ok":true,"service":"sector-telemetry","version":"0.1.0"}
```

## 11. Проверить events API

`app_start`:

```bash
curl -X POST https://telemetry.sector-map.ru/api/v1/events \
  -H 'Content-Type: application/json' \
  -H "X-App-Token: $APP_TOKEN" \
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

`heartbeat`:

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

`app_background`:

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

Успешный ответ:

```json
{"ok":true}
```

## 12. Проверить admin API

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/summary \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/devices \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

```bash
curl 'https://telemetry.sector-map.ru/api/v1/admin/events?limit=100' \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

Если `ADMIN_TOKEN` не задан, admin API возвращает `503`, а не открывается без токена.

## 13. Обслуживание

Команды выполняются из `/opt/sector-telemetry`:

```bash
docker compose -p sector-telemetry ps
docker compose -p sector-telemetry logs --tail=100
docker compose -p sector-telemetry restart sector-telemetry
docker compose -p sector-telemetry down
docker compose -p sector-telemetry up -d
```

## 14. Backup SQLite

Данные лежат в:

```text
/opt/sector-telemetry/data/telemetry.db
```

Надежный backup делать с остановкой backend-контейнера:

```bash
cd /opt/sector-telemetry
docker compose -p sector-telemetry stop sector-telemetry
cp ./data/telemetry.db "./data/telemetry-$(date +%F-%H%M%S).db"
docker compose -p sector-telemetry up -d
```

Если `sqlite3` установлен на хосте, можно использовать `.backup`:

```bash
sqlite3 ./data/telemetry.db ".backup ./data/telemetry-$(date +%F-%H%M%S).db"
```

Файлы `telemetry.db-shm` и `telemetry.db-wal` - нормальные SQLite WAL-файлы. Не удалять их вручную при работающем контейнере.

## 15. Restore SQLite

```bash
cd /opt/sector-telemetry
docker compose -p sector-telemetry stop sector-telemetry
cp ./data/telemetry-YYYY-MM-DD-HHMMSS.db ./data/telemetry.db
sudo chown 10001:10001 ./data/telemetry.db
sudo chmod 640 ./data/telemetry.db
docker compose -p sector-telemetry up -d
curl -i https://telemetry.sector-map.ru/health
```

После restore проверить admin summary:

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/summary \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

## 16. Troubleshooting

- HTTP 502 на `/health` означает, что Caddy не может достучаться до backend. Проверить `docker compose -p sector-telemetry ps`, логи Caddy/backend и имя upstream `sector-telemetry:8080`.
- `unable to open database file` означает проблему прав на `data/`. Выполнить `sudo chown -R 10001:10001 /opt/sector-telemetry/data` и `sudo chmod 750 /opt/sector-telemetry/data`, затем перезапустить backend.
- Caddy получает HTTPS-сертификат не мгновенно. Можно подождать и проверить `docker compose -p sector-telemetry logs --tail=100 caddy`.
- Если `sector-map.ru` имеет две A-записи, удалить лишнюю у DNS-провайдера и дождаться обновления DNS.
- Если `git clone` в `/opt` дает `Permission denied`, создать каталог через `sudo mkdir -p` и передать владельца обычному пользователю через `sudo chown "$USER:$USER"`.
- Если git пишет `dubious ownership`, значит clone или файлы создавались через `sudo`. Исправить владельца каталога: `sudo chown -R "$USER:$USER" /path/to/repo`.

## 17. Что важно

- Не публиковать backend `:8080` наружу.
- Открывать наружу только Caddy `80/443`.
- Не хранить `.env`, SQLite database, backup-файлы, tokens, APK/AAB или keystore в git.
- Не отправлять координаты, азимуты, маршруты, замеры или позывной в telemetry endpoint.
