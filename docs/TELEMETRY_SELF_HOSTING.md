# Self-hosting telemetry backend

Инструкция для запуска backend технической статистики на VPS Debian/Ubuntu.

## 1. Домен и DNS

Купить или настроить домен `sector-map.ru`.

DNS-записи:

```text
A sector-map.ru -> VPS IP
A telemetry.sector-map.ru -> VPS IP
A www.sector-map.ru -> VPS IP  # optional
```

Основной домен `sector-map.ru` может быть простой заглушкой. Telemetry API работает на `telemetry.sector-map.ru`.

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

Скопировать файлы из `backend/`:

```text
Dockerfile
docker-compose.example.yml
Caddyfile.example
.env.example
cmd/
internal/
migrations/
go.mod
```

## 4. Настроить env

```bash
cp .env.example .env
```

Заполнить:

```env
APP_TOKEN=public-filter-token-for-your-apk
ADMIN_TOKEN=long-random-admin-token
PUBLIC_BASE_URL=https://telemetry.sector-map.ru
```

Для Telegram:

```env
TELEGRAM_REPORT_ENABLED=true
TELEGRAM_BOT_TOKEN=telegram-bot-token
TELEGRAM_CHAT_ID=telegram-chat-id
TELEGRAM_REPORT_TIME=08:00
TELEGRAM_REPORT_TIMEZONE=Europe/Tallinn
```

Не хранить `.env` в git и не публиковать реальные токены.

## 5. Проверить Caddy

`Caddyfile.example` содержит:

```text
telemetry.sector-map.ru {
    handle /api/* {
        reverse_proxy sector-telemetry:8080
    }

    handle /health {
        reverse_proxy sector-telemetry:8080
    }

    handle {
        respond "sector telemetry backend" 200
    }
}
```

Для production можно скопировать:

```bash
cp Caddyfile.example Caddyfile
```

И в compose заменить mount на `./Caddyfile:/etc/caddy/Caddyfile:ro`, если нужно отделить пример от рабочей конфигурации.

## 6. Запустить

```bash
docker compose -f docker-compose.example.yml up -d --build
```

Проверить состояние:

```bash
docker compose -f docker-compose.example.yml ps
docker compose -f docker-compose.example.yml logs -f sector-telemetry
```

## 7. Проверить health endpoint

```bash
curl https://telemetry.sector-map.ru/health
```

Ожидается:

```json
{"ok":true,"service":"sector-telemetry","version":"0.1.0"}
```

## 8. Проверить event endpoint

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

Ожидается:

```json
{"ok":true}
```

## 9. Проверить admin summary

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/summary \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

Если `ADMIN_TOKEN` не задан, endpoint должен вернуть `503`, а не открытый API.

## 10. Проверить Telegram report

Если Telegram включен, дождаться времени `TELEGRAM_REPORT_TIME`.

Для быстрой ручной проверки можно временно поставить ближайшее время и перезапустить сервис:

```bash
docker compose -f docker-compose.example.yml restart sector-telemetry
```

Если token/chat id пустые, сервис не падает и пишет warning в logs.

## 11. Backup

Данные лежат в bind mount:

```text
/opt/sector-telemetry/data/telemetry.db
```

Backup через sqlite:

```bash
sqlite3 ./data/telemetry.db ".backup ./data/telemetry-$(date +%F).db"
```

Если `sqlite3` на хосте не установлен, можно остановить backend на время копирования:

```bash
docker compose -f docker-compose.example.yml stop sector-telemetry
cp ./data/telemetry.db ./data/telemetry-$(date +%F).db
docker compose -f docker-compose.example.yml up -d
```

## 12. Restore

```bash
docker compose -f docker-compose.example.yml stop sector-telemetry
cp ./data/telemetry-YYYY-MM-DD.db ./data/telemetry.db
docker compose -f docker-compose.example.yml up -d
```

После restore проверить:

```bash
curl https://telemetry.sector-map.ru/api/v1/admin/summary \
  -H "X-Admin-Token: $ADMIN_TOKEN"
```

## 13. Что важно

- Не публиковать backend `:8080` наружу.
- Открывать наружу только Caddy `80/443`.
- Не хранить `.env`, SQLite database, tokens, APK/AAB или keystore в git.
- Не отправлять координаты, азимуты, маршруты и замеры в telemetry endpoint.
