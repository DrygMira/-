# API для разработчиков: Авторизация и сессии

## Статус документа

Это **первая глава API-спецификации для клиентов**.

Документ фиксирует:

- единый JSON-формат запросов и ответов по WebSocket;
- роли `device key`, `session_key` и `storagePwd`;
- целевой формат подписываемых строк для авторизации;
- совместимость между текущей реализацией сервера и предлагаемым расширением.

---

## 1. Транспортный конверт

Все клиентские вызовы идут через WebSocket в общем JSON-конверте:

```json
{
  "op": "OperationName",
  "requestId": "req-001",
  "payload": {
  }
}
```

### Поля запроса

- `op` — имя операции.
- `requestId` — уникальный идентификатор запроса на стороне клиента.
- `payload` — объект с параметрами операции.

### Базовый формат ответа

Успешный ответ:

```json
{
  "requestId": "req-001",
  "status": 200,
  "payload": {
  }
}
```

Ответ с ошибкой:

```json
{
  "requestId": "req-001",
  "status": 400,
  "error": "BAD_REQUEST",
  "message": "Human readable description"
}
```

### Общие правила

- Все строки подписи и challenge собираются в UTF-8.
- Временные метки передаются в `timeMs` как Unix time в миллисекундах.
- Бинарные поля передаются как Base64-строки.
- `requestId` должен возвращаться сервером без изменений.

---

## 2. Роли ключей и секретов

### `device key`

Постоянный ключ устройства или аккаунта, которым клиент подтверждает право создать новую сессию.

Используется для:

- `CreateAuthSession`

### `session_key`

Клиент **сам создаёт** отдельный ключ сессии и передаёт на сервер только публичную часть.

Этот ключ используется для:

- `SessionLogin`
- последующих перевходов в уже созданную сессию

В API клиент передаёт `sessionKey` целиком одной строкой, и сервер хранит `active_sessions.session_key` тоже целиком одной строкой.

### `storagePwd`

`storagePwd` тоже **генерируется и передаётся клиентом** при создании сессии.

Сервер:

- сохраняет это значение в составе активной сессии;
- возвращает его клиенту после успешного `SessionLogin`.

Это нужно, чтобы клиент мог восстановить доступ к локально/серверно зашифрованному хранилищу сессии.

---

## 3. Формат `session_key` с префиксом алгоритма

Чтобы поддерживать разные аппаратные и программные типы ключей, `session_key` рекомендуется хранить и передавать не как "просто base64", а как строку с явным префиксом алгоритма:

```text
<algorithm>/<public-key-data>
```

Примеры:

```text
ed25519/MCowBQYDK2VwAyEA2I7...
secp256r1/BBD9LVa8gk9...
rsa2048/MIIBIjANBgkqh...
```

### Зачем это нужно

- у разных устройств разный набор аппаратно поддерживаемых ключей;
- серверу проще понимать, какой верификатор использовать;
- формат можно расширять без миграции всей таблицы сессий.

### Рекомендация по полю API

Во внешнем API лучше использовать поле:

```json
{
  "sessionKey": "ed25519/BASE64_PUBLIC_KEY"
}
```

Если сервер внутри пока хранит старое поле `sessionPubKeyB64`, допускается переходный слой, который:

- принимает `sessionKey`;
- разбирает префикс алгоритма;
- сохраняет алгоритм и публичный ключ раздельно либо в одном поле.

---

## 4. Поток авторизации

Поддерживаются два базовых сценария:

1. Создание новой сессии.
2. Вход в существующую сессию.

---

## 5. Создание новой сессии

### Шаг 1. `AuthChallenge`

Клиент запрашивает nonce для логина.

Запрос:

```json
{
  "op": "AuthChallenge",
  "requestId": "auth-001",
  "payload": {
    "login": "alice"
  }
}
```

Успешный ответ:

```json
{
  "requestId": "auth-001",
  "status": 200,
  "payload": {
    "login": "alice",
    "authNonce": "8f2f0f71-0b1c-4ab2-8f5d-0bc5d6f6aa11",
    "expiresInMs": 30000
  }
}
```

Назначение:

- сервер убеждается, что пользователь существует;
- сервер связывает `authNonce` с текущим WebSocket-соединением;
- nonce одноразовый и живёт ограниченное время.

### Шаг 2. `CreateAuthSession`

Клиент:

- генерирует новый `session_key`;
- генерирует или выбирает `storagePwd`;
- подписывает строку создания сессии своим `device key`.

#### Целевой формат запроса

```json
{
  "op": "CreateAuthSession",
  "requestId": "create-001",
  "payload": {
    "login": "alice",
    "sessionKey": "ed25519/BASE64_PUBLIC_KEY",
    "storagePwd": "BASE64_OR_APP_SPECIFIC_SECRET",
    "timeMs": 1774600000123,
    "authNonce": "8f2f0f71-0b1c-4ab2-8f5d-0bc5d6f6aa11",
    "deviceKey": "BASE64_DEVICE_PUBLIC_KEY",
    "signatureB64": "BASE64_SIGNATURE_BY_DEVICE_KEY",
    "clientInfo": "Android 15; Pixel 9"
  }
}
```

#### Целевая строка для подписи

Рекомендуемый формат:

```text
AUTH_CREATE_SESSION:{login}:{sessionKey}:{storagePwd}:{timeMs}:{authNonce}
```

Пример:

```text
AUTH_CREATE_SESSION:alice:ed25519/BASE64_PUBLIC_KEY:BASE64_OR_APP_SPECIFIC_SECRET:1774600000123:8f2f0f71-0b1c-4ab2-8f5d-0bc5d6f6aa11
```

### Почему `sessionKey` и `storagePwd` нужно включить в подпись

- сервер получает криптографическое подтверждение того, какие именно значения утвердил клиент;
- снижается риск подмены `session_key` между клиентом и сервером;
- `storagePwd` становится частью подтверждённого набора параметров создания сессии.

### Дополнительная проверка `deviceKey`

Перед проверкой подписи сервер должен:

1. загрузить актуальный `device_key` пользователя;
2. сравнить его со значением `payload.deviceKey`;
3. только после совпадения ключей проверять подпись.

Если ключ не совпадает, сервер должен возвращать ошибку о том, что ключ не соответствует актуальной версии.

На будущее:

- для сценария обновления `device_key` желательно добавить дополнительную проверку актуального ключа через Solana;
- если и после этого ключ не подтверждается, сервер всё равно должен возвращать ошибку о несовпадении актуального ключа.

### Успешный ответ

```json
{
  "requestId": "create-001",
  "status": 200,
  "payload": {
    "sessionId": "sess_7c5e5c4b",
    "sessionKey": "ed25519/BASE64_PUBLIC_KEY",
    "createdAtMs": 1774600000201
  }
}
```

---

## 6. Вход в существующую сессию

### Шаг 1. `SessionChallenge`

Запрос:

```json
{
  "op": "SessionChallenge",
  "requestId": "sch-001",
  "payload": {
    "sessionId": "sess_7c5e5c4b"
  }
}
```

Успешный ответ:

```json
{
  "requestId": "sch-001",
  "status": 200,
  "payload": {
    "sessionId": "sess_7c5e5c4b",
    "nonce": "0e5bb0f4-c7d8-4efb-b44d-bf31a6126c66",
    "expiresInMs": 30000,
    "sessionKeyAlgorithm": "ed25519"
  }
}
```

### Шаг 2. `SessionLogin`

Клиент подписывает challenge приватной частью соответствующего `session_key`.

Запрос:

```json
{
  "op": "SessionLogin",
  "requestId": "slogin-001",
  "payload": {
    "sessionId": "sess_7c5e5c4b",
    "sessionKey": "ed25519/BASE64_PUBLIC_KEY",
    "timeMs": 1774600010456,
    "signatureB64": "BASE64_SIGNATURE_BY_SESSION_KEY",
    "clientInfo": "Android 15; Pixel 9"
  }
}
```

Строка для подписи:

```text
SESSION_LOGIN:{sessionId}:{timeMs}:{nonce}
```

Пример:

```text
SESSION_LOGIN:sess_7c5e5c4b:1774600010456:0e5bb0f4-c7d8-4efb-b44d-bf31a6126c66
```

### Дополнительная проверка `sessionKey`

Перед проверкой подписи сервер должен:

1. загрузить `active_sessions.session_key` по `sessionId`;
2. сравнить его со значением `payload.sessionKey`;
3. только после совпадения ключей проверять подпись.

Если ключ не совпадает, сервер должен возвращать ошибку о том, что ключ не соответствует актуальной версии.

Успешный ответ:

```json
{
  "requestId": "slogin-001",
  "status": 200,
  "payload": {
    "sessionId": "sess_7c5e5c4b",
    "storagePwd": "BASE64_OR_APP_SPECIFIC_SECRET",
    "authenticatedAtMs": 1774600010500
  }
}
```

---

## 7. Работа со списком сессий

### `ListSessions`

Доступно только после успешного `SessionLogin`.

Запрос:

```json
{
  "op": "ListSessions",
  "requestId": "list-001",
  "payload": {
  }
}
```

Успешный ответ:

```json
{
  "requestId": "list-001",
  "status": 200,
  "payload": {
    "sessions": [
      {
        "sessionId": "sess_7c5e5c4b",
        "sessionKey": "ed25519/BASE64_PUBLIC_KEY",
        "clientInfo": "Android 15; Pixel 9",
        "lastAuthenticatedAtMs": 1774600010500,
        "createdAtMs": 1774600000201,
        "geo": "RU/Moscow"
      }
    ]
  }
}
```

### `CloseActiveSession`

Доступно только после успешного `SessionLogin`.

Запрос:

```json
{
  "op": "CloseActiveSession",
  "requestId": "close-001",
  "payload": {
    "sessionId": "sess_7c5e5c4b"
  }
}
```

Успешный ответ:

```json
{
  "requestId": "close-001",
  "status": 200,
  "payload": {
    "closed": true,
    "sessionId": "sess_7c5e5c4b"
  }
}
```

---

## 8. Ошибки и коды отказа

Минимально стоит стандартизовать такие ответы:

- `400 BAD_REQUEST` — не хватает поля или неверный формат.
- `401 UNAUTHORIZED` — challenge не был пройден или соединение не авторизовано.
- `403 INVALID_SIGNATURE` — подпись не прошла проверку.
- `403 DEVICE_KEY_NOT_ACTUAL` — присланный `deviceKey` не совпадает с актуальным ключом пользователя.
- `403 SESSION_KEY_NOT_ACTUAL` — присланный `sessionKey` не совпадает с актуальным ключом сессии.
- `404 SESSION_NOT_FOUND` — сессия не существует или уже закрыта.
- `409 NONCE_ALREADY_USED` — challenge уже использован.
- `410 CHALLENGE_EXPIRED` — nonce устарел.
- `422 UNSUPPORTED_KEY_ALGORITHM` — префикс `session_key` не поддерживается сервером.
- `429 TOO_MANY_ATTEMPTS` — лимит попыток исчерпан.

Пример:

```json
{
  "requestId": "create-001",
  "status": 422,
  "error": "UNSUPPORTED_KEY_ALGORITHM",
  "message": "sessionKey prefix is not supported"
}
```

---

## 9. Совместимость с текущей реализацией сервера

По текущему состоянию кода сервер уже использует схему:

- `AuthChallenge(login)`
- `CreateAuthSession(login, sessionKey, storagePwd, timeMs, authNonce, deviceKey, signatureB64, clientInfo)`
- `SessionChallenge(sessionId)`
- `SessionLogin(sessionId, sessionKey, timeMs, signatureB64, clientInfo)`

Текущая строка подписи для `CreateAuthSession` в коде:

```text
AUTH_CREATE_SESSION:{login}:{sessionKey}:{storagePwd}:{timeMs}:{authNonce}
```

Перед проверкой подписи сервер также должен сверять:

- `payload.deviceKey` с актуальным `solana_users.device_key`;
- `payload.sessionKey` с актуальным `active_sessions.session_key`.

### Рекомендуемый путь миграции

1. Ввести новую версию контракта `CreateAuthSession`.
2. На сервере хранить `session_key` целиком одной строкой.
3. На сервере распознавать префикс алгоритма в `sessionKey`.
4. В `CreateAuthSession` передавать и сверять `deviceKey`.
5. В `SessionLogin` передавать и сверять `sessionKey`.
6. Использовать подпись строки:

```text
AUTH_CREATE_SESSION:{login}:{sessionKey}:{storagePwd}:{timeMs}:{authNonce}
```

---

## 10. Практические требования к клиентам

- Клиент должен сам хранить приватную часть `session_key`.
- Приватная часть `device key` никогда не отправляется на сервер.
- `session_key` должен быть новым для каждой новой сессии.
- `storagePwd` должен генерироваться как криптографически стойкое значение.
- Клиент должен учитывать допустимый дрейф времени и синхронизацию часов.
- Клиент не должен повторно использовать старый `authNonce` или `nonce`.

---

## 11. Короткое резюме

- Да, клиент сам создаёт `session_key`.
- Да, клиент сам передаёт `storagePwd`.
- Для `session_key` имеет смысл ввести префикс алгоритма, например `ed25519/...`.
- Для `CreateAuthSession` клиент должен дополнительно передавать `deviceKey`, а сервер должен сверять его с актуальным ключом пользователя.
- Для `SessionLogin` клиент должен дополнительно передавать `sessionKey`, а сервер должен сверять его с актуальным ключом сессии.
- Для `CreateAuthSession` рекомендуется подписывать не только `login`, `timeMs` и `authNonce`, но также `sessionKey` и `storagePwd`.
- Для разработчиков клиентов лучше сразу документировать API через полные JSON-примеры запросов и ответов.
