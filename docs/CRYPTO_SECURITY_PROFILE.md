# Профиль криптографической безопасности

Этот документ фиксирует целевую модель deployment/server modes, relay-only обработки данных и будущих server profiles в клиенте Sector. Он описывает требования к архитектуре, а не реализованный код.

Sector остается local-first приложением. Серверная часть не должна становиться постоянным облачным архивом пользовательских данных: базовая модель для приватных объектов, заметок, координат, live location и медиа - encrypted relay между доверенными участниками.

## deployment and server modes

Клиент не должен быть жестко привязан к одному серверу. Будущий блок настроек "сервер и синхронизация" должен позволять выбрать local-only режим или указать custom/self-hosted сервер с явной проверкой capabilities.

### local_only

- без сервера;
- все данные только на устройстве;
- обмен только через локальный export/import;
- подходит для максимальной автономности.

### dev_test

- только для разработки;
- no-op crypto допустим только здесь;
- реальные персональные данные запрещены;
- реальные координаты, заметки и контакты запрещены.

### rf_production

- официальный или управляемый сервер в РФ;
- primary database, logs, backups и object storage только в РФ;
- E2E обязательно;
- cross-border transfer disabled by default;
- foreign analytics, crash reporting и object storage запрещены.

### international_production

- отдельный контур для нероссийских пользователей;
- не смешивать с RF-контуром;
- российские пользователи не должны попадать туда без отдельной legal/security review.

### private_self_hosted

- пользователь или небольшая группа указывает свой сервер;
- сервер может быть домашним или частным;
- владелец сервера отвечает за контур, эксплуатацию и правовую оценку;
- приложение должно показывать предупреждение, что сервер не управляется разработчиком Sector;
- E2E обязательно для отправки sensitive payload.

### regulated_self_hosted

- сервер разворачивается у организации или заказчика;
- без иностранных SaaS;
- без внешней аналитики;
- без внешнего crash reporting;
- без внешнего object storage;
- crypto provider должен быть заменяемым;
- web-карта приватных данных отключена по умолчанию.

### relay_only_server

- сервер не является постоянным хранилищем пользовательских данных;
- сервер временно передает encrypted payload между участниками;
- encrypted payload и encrypted media хранятся только до доставки или TTL;
- сервер хранит минимальные служебные данные;
- пользовательские backups только локально.

## relay-only data handling

В relay-only модели сервер хранит только минимальные служебные данные: `account_id`, `device_id`, public keys, fingerprints, contact relations, refresh token hashes, delivery metadata и security logs. Эти данные нужны для идентичности, доставки, контроля доступа и аудита, но не должны превращаться в пользовательский архив.

Приватные данные передаются только как encrypted payload:

- заметки, медиа, геопозиция, live location и азимутные лучи передаются только как encrypted payload;
- сервер не должен видеть plaintext;
- encrypted media blobs тоже временные;
- encrypted payload и encrypted media blob хранятся только до получения адресатом или до `expires_at` / TTL;
- после доставки или истечения TTL данные должны удаляться по server policy;
- media TTL и delete-after-delivery должны быть частью server policy;
- live location по умолчанию хранит только последнюю encrypted point и короткий TTL.

Для медиа заметок клиент должен явно спрашивать пользователя: "в заметке есть фото/аудио. отправить вместе с медиа?".

- если пользователь выбирает "без медиа", отправляется только encrypted note payload без файлов;
- если пользователь выбирает "с медиа", фото и аудио шифруются на клиенте и отправляются как encrypted media blobs;
- attachment metadata, если она раскрывает приватный контекст, должна находиться внутри encrypted payload.

Постоянный backup пользовательских данных должен быть локальным: encrypted zip / sector-backup, который пользователь сам хранит и переносит. Серверный backup пользовательского архива не является базовой функцией.

## account identity without mandatory email

Email не является обязательной частью базовой identity model Sector. Базовая модель должна минимизировать персональные данные и строиться вокруг криптографической идентичности устройства, а не вокруг email-адреса, телефона или другого внешнего персонального идентификатора.

Базовая identity:

- `account_id`;
- `device_id`;
- device public key;
- key fingerprint;
- optional display name / callsign.

Приватный ключ остается на устройстве и никогда не отправляется на сервер. Сервер может хранить device public key и fingerprint для доставки encrypted payload и проверки доверия, но не хранит private keys и не должен иметь материал, достаточный для расшифрования E2E-данных.

Серверу не нужен email для E2E sharing. Контакты и trusted contacts должны строиться через QR, invite code, ссылку или ручное подтверждение fingerprint, а не через доверие к email-адресу.

Account recovery не должен ломать E2E. Recovery phrase / recovery key принадлежит пользователю и используется для переноса аккаунта или ключевого материала на новое устройство. Если recovery phrase / recovery key потерян, server-side recovery без этого ключа не должен расшифровывать старые E2E-данные.

Запрещено:

- требовать email для базовой регистрации;
- использовать телефон, IMEI или Android ID как identity;
- делать восстановление E2E-данных через серверный master key;
- хранить recovery phrase на сервере в открытом виде;
- привязывать trusted contact к email без fingerprint confirmation.

## optional email mode

Email может быть включен отдельным сервером только как optional feature. Он не должен быть required для `private_self_hosted` и `regulated_self_hosted`; такие режимы могут полностью отключать email и строить аккаунты через `account_id`, `device_id`, public key и fingerprint.

Если email включен, он считается персональными данными и требует отдельной политики, правового основания, retention rules и пользовательского предупреждения. Email verification применима только если сервер явно включает email-based accounts или email login.

Отсутствие email не отменяет остальные требования по безопасности, E2E, server capabilities, data residency и персональным данным. Минимизация персональных данных остается базовым архитектурным требованием.

## client server profile requirements

Будущий профиль сервера в клиенте должен хранить параметры, полученные от пользователя и подтвержденные через server capabilities:

- `server_url`;
- `port`;
- `tls_required`;
- `server_certificate_fingerprint`;
- `deployment_mode`;
- `crypto_profile`;
- `data_residency`;
- `operator_name`;
- `capabilities`;
- `last_verified_at`.

При подключении сервер должен объявлять capabilities, deployment mode, data residency, crypto profile и operator info. Клиент должен показывать эти значения перед подтверждением подключения, особенно для custom/self-hosted серверов.

Клиент должен показывать предупреждения:

- custom server warning;
- dev/noop server warning;
- foreign server / data residency warning;
- weak/no E2E warning;
- certificate/fingerprint changed warning.

Запрещено:

- автоматически отправлять sensitive payload на сервер без проверки capabilities;
- скрыто обращаться к официальному серверу Sector при custom/self-hosted режиме;
- fallback на plaintext;
- отправлять координаты, заметки или live location на dev/noop сервер;
- отправлять sensitive payload, если сервер не поддерживает `production_e2e` или regulated crypto profile;
- хранить приватные ключи на сервере.

Если certificate fingerprint изменился, клиент должен остановить отправку sensitive payload до повторного явного подтверждения пользователем или администратором контура.
