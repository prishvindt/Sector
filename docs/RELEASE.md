# Релизный процесс

Документ описывает ручной релиз приложения «Сектор» вне Google Play.

## Ветки

- `develop` — тестовая ветка для проверки следующей версии.
- `main` — релизная ветка.
- `feature/*`, `docs/*` и похожие ветки — временные ветки для отдельных задач.

Релиз делается из `main`. Перед merge `develop -> main` нужна ручная проверка.

## Перед релизом

1. Проверить, что нужные изменения уже находятся в `develop`.
2. Выполнить ручную проверку приложения.
3. Поднять `versionName` и `versionCode` до релиза.
4. Собрать release APK.
5. Проверить, что APK называется `Sector-<version>-release.apk`.
6. Убедиться, что версия APK, GitHub Release и будущий `update.json` описывают одну и ту же версию.

`versionCode` должен увеличиваться на каждом релизе. Если `versionCode` в `update.json` не больше установленного `versionCode`, приложение не покажет обновление.

## Merge в main

1. После ручной проверки выполнить merge `develop -> main`.
2. Не обновлять `update.json` до публикации APK.
3. Не добавлять APK, AAB, keystore и `local.properties` в git.

## GitHub Release

1. Создать GitHub Release после merge в `main`.
2. Загрузить APK в Release assets.
3. Имя APK должно быть:

```text
Sector-<version>-release.apk
```

Пример:

```text
Sector-0.1.7-release.apk
```

## update.json

`update.json` лежит в `main`.

Обновлять `update.json` можно только после публикации APK в GitHub Release.

Поля:

- `latestVersion` — версия для показа пользователю.
- `versionCode` — код версии, больше предыдущего установленного.
- `apkUrl` — ссылка на опубликованный APK из GitHub Release.
- `changelog` — краткий список изменений.
- `mandatory` — обязательность обновления.

Порядок:

1. Опубликовать GitHub Release с APK.
2. Скопировать URL опубликованного APK.
3. Обновить `update.json` в `main`.
4. Проверить, что `apkUrl` открывается без авторизации.
5. После обновления `update.json` синхронизировать `develop` с `main`.

## Backend телеметрии и update.json

Изменения backend-документации и backend-кода в `backend/` не требуют обновления `update.json`, если не меняется Android APK.

`update.json` относится только к Android APK: версии приложения, `versionCode`, ссылке на APK и changelog для встроенного обновления.

Telemetry backend выпускается и разворачивается отдельно от Android-релиза на VPS. Он не должен менять `versionName`, `versionCode` или правила публикации APK.

После изменения backend-кода нужно отдельно проверить:

- `go test ./...`;
- `docker build`;
- `docker compose config`;
- `/health`;
- test event;
- admin summary;
- Telegram test.

Для документационных backend-задач достаточно проверить Markdown diff и убедиться, что Android-код, backend Go-код и `update.json` не менялись.

## Проверка обновления

После публикации:

1. Установить на телефон предыдущую версию приложения.
2. Запустить проверку обновлений.
3. Убедиться, что приложение видит новую версию.
4. Скачать APK через приложение.
5. Проверить запуск системного установщика Android.
6. После установки проверить номер версии в приложении.

## Что не делать

- Не обновлять `update.json` в feature/docs-ветках.
- Не менять `versionName` и `versionCode` после публикации Release без нового релизного решения.
- Не публиковать `update.json` раньше APK.
- Не указывать в `apkUrl` локальный файл или временную ссылку.
- Не хранить release APK, AAB, keystore и `local.properties` в репозитории.
- Не менять GitHub Actions без отдельной задачи.
