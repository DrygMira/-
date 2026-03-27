# API для разработчиков: Авторизация

Этот файл описывает именно этапы авторизации клиента, то есть как создать новую сессию и как войти в уже существующую.

Здесь четыре метода:

- `AuthChallenge`
- `CreateAuthSession`
- `SessionChallenge`
- `SessionLogin`

Логика раздела такая:

- сначала клиент либо начинает создание новой сессии через `deviceKey`;
- либо начинает вход в уже созданную сессию через `sessionKey`;
- сервер на первом шаге выдаёт challenge/nonce;
- на втором шаге клиент присылает подписанный ответ;
- сервер сверяет актуальные публичные ключи и только потом проверяет подпись.

Ниже в документе сначала описан сценарий, а потом зафиксированы точные форматы запросов и ответов.

## 1. Поток авторизации

Поддерживаются два сценария:

1. Создание новой сессии:
   `AuthChallenge` -> `CreateAuthSession`
2. Вход в существующую сессию:
   `SessionChallenge` -> `SessionLogin`

`deviceKey` используется для создания новой сессии.

`sessionKey` используется для входа в уже созданную сессию.

`sessionKey` передаётся и хранится целиком одной строкой, например:

```text
ed25519/BASE64_PUBLIC_KEY
```

---

## 2. `AuthChallenge`

### Запрос

```json
{
  "op": "AuthChallenge",
  "requestId": "auth-001",
  "payload": {
    "login": "alice"
  }
}
```

### Успешный ответ

```json
{
  "op": "AuthChallenge",
  "requestId": "auth-001",
  "status": 200,
  "ok": true,
  "payload": {
    "authNonce": "8f2f0f71-0b1c-4ab2-8f5d-0bc5d6f6aa11"
  }
}
```

### Специфические коды ошибок `AuthChallenge`

- `400 / EMPTY_LOGIN` — пустой `login`.
- `400 / ALREADY_AUTHED` — по текущему соединению уже выполнена авторизация.
- `422 / UNKNOWN_USER` — пользователь с таким `login` не найден.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера, если появится вне штатного сценария.

---

## 3. `CreateAuthSession`

### Запрос

```json
{
  "op": "CreateAuthSession",
  "requestId": "create-001",
  "payload": {
    "login": "alice",
    "sessionKey": "ed25519/BASE64_PUBLIC_KEY",
    "storagePwd": "BASE64_OR_APP_SPECIFIC_SECRET",
    "timeMs": 1774600000123,
    "authNonce": "nonce",
    "deviceKey": "BASE64_DEVICE_PUBLIC_KEY",
    "signatureB64": "BASE64_SIGNATURE",
    "clientInfo": "Android 15; Pixel 9"
  }
}
```

### Строка для подписи

```text
AUTH_CREATE_SESSION:{login}:{sessionKey}:{storagePwd}:{timeMs}:{authNonce}
```

### Дополнительная проверка ключа

Перед проверкой подписи сервер должен:

1. взять актуальный `solana_users.device_key`;
2. сравнить его с `payload.deviceKey`;
3. только потом проверять подпись.

Если ключ не совпадает, сервер возвращает ошибку `DEVICE_KEY_NOT_ACTUAL`.

На будущее:

- для ротации `device_key` желательно добавить перепроверку через Solana.

### Успешный ответ

```json
{
  "op": "CreateAuthSession",
  "requestId": "create-001",
  "status": 200,
  "ok": true,
  "payload": {
    "sessionId": "sess_7c5e5c4b"
  }
}
```

### Специфические коды ошибок `CreateAuthSession`

- `400 / NO_STEP1_CONTEXT` — для данного соединения не был корректно выполнен `AuthChallenge`.
- `400 / EMPTY_LOGIN` — пустой `login`.
- `400 / LOGIN_MISMATCH` — `login` не совпадает с тем, для кого был выдан `authNonce`.
- `501 / DB_ERROR_USER_LOOKUP` — ошибка БД при повторном чтении пользователя.
- `422 / USER_NOT_FOUND` — пользователь не найден.
- `501 / NO_LOGIN` — у пользователя на сервере не заполнен `login`.
- `400 / EMPTY_STORAGE_PWD` — пустой `storagePwd`.
- `400 / EMPTY_SESSION_KEY` — пустой `sessionKey`.
- `422 / UNSUPPORTED_KEY_ALGORITHM` — префикс алгоритма в `sessionKey` или `deviceKey` не поддерживается текущим сервером.
- `400 / BAD_BASE64` — неверный Base64 в `sessionKey`, `deviceKey` или `signatureB64`.
- `400 / EMPTY_SIGNATURE` — пустая подпись.
- `400 / TIME_SKEW` — время клиента отличается от серверного больше допустимого окна.
- `400 / NO_DEVICE_KEY` — у пользователя в БД отсутствует `deviceKey`.
- `400 / EMPTY_AUTH_NONCE` — пустой `authNonce`.
- `400 / AUTH_NONCE_MISMATCH` — `authNonce` не соответствует значению из `AuthChallenge`.
- `400 / EMPTY_DEVICE_KEY` — в запросе не передан `deviceKey`.
- `422 / DEVICE_KEY_NOT_ACTUAL` — `deviceKey` не совпадает с актуальной версией на сервере.
- `422 / BAD_SIGNATURE` — подпись не прошла проверку.
- `501 / DB_ERROR_SESSION_CREATE` — ошибка БД при создании записи активной сессии.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 4. `SessionChallenge`

### Запрос

```json
{
  "op": "SessionChallenge",
  "requestId": "sch-001",
  "payload": {
    "sessionId": "sess_7c5e5c4b"
  }
}
```

### Успешный ответ

```json
{
  "op": "SessionChallenge",
  "requestId": "sch-001",
  "status": 200,
  "ok": true,
  "payload": {
    "nonce": "0e5bb0f4-c7d8-4efb-b44d-bf31a6126c66"
  }
}
```

### Специфические коды ошибок `SessionChallenge`

- `400 / EMPTY_SESSION_ID` — пустой `sessionId`.
- `501 / DB_ERROR` — ошибка БД при чтении сессии.
- `422 / SESSION_NOT_FOUND` — сессия не найдена.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 5. `SessionLogin`

### Запрос

```json
{
  "op": "SessionLogin",
  "requestId": "slogin-001",
  "payload": {
    "sessionId": "sess_7c5e5c4b",
    "sessionKey": "ed25519/BASE64_PUBLIC_KEY",
    "timeMs": 1774600010456,
    "signatureB64": "BASE64_SIGNATURE",
    "clientInfo": "Android 15; Pixel 9"
  }
}
```

### Строка для подписи

```text
SESSION_LOGIN:{sessionId}:{timeMs}:{nonce}
```

### Дополнительная проверка ключа

Перед проверкой подписи сервер должен:

1. взять `active_sessions.session_key`;
2. сравнить его с `payload.sessionKey`;
3. только потом проверять подпись.

Если ключ не совпадает, сервер возвращает ошибку `SESSION_KEY_NOT_ACTUAL`.

### Успешный ответ

```json
{
  "op": "SessionLogin",
  "requestId": "slogin-001",
  "status": 200,
  "ok": true,
  "payload": {
    "storagePwd": "BASE64_OR_APP_SPECIFIC_SECRET"
  }
}
```

### Специфические коды ошибок `SessionLogin`

- `400 / EMPTY_SESSION_ID` — пустой `sessionId`.
- `400 / NO_CHALLENGE` — перед `SessionLogin` не был успешно выполнен `SessionChallenge` либо nonce уже истёк.
- `400 / SESSION_ID_MISMATCH` — nonce был выдан для другого `sessionId`.
- `400 / TIME_SKEW` — время клиента отличается от серверного больше допустимого окна.
- `400 / EMPTY_SIGNATURE` — пустая подпись.
- `400 / EMPTY_SESSION_KEY` — пустой `sessionKey`.
- `501 / DB_ERROR` — ошибка БД при чтении сессии.
- `422 / SESSION_NOT_FOUND` — сессия не найдена.
- `501 / NO_SESSION_KEY` — у сессии отсутствует `session_key`.
- `422 / SESSION_KEY_NOT_ACTUAL` — переданный `sessionKey` не совпадает с актуальной версией на сервере.
- `422 / UNSUPPORTED_KEY_ALGORITHM` — префикс алгоритма в `sessionKey` не поддерживается текущим сервером.
- `400 / BAD_BASE64` — неверный Base64 в `sessionKey` или `signatureB64`.
- `422 / BAD_SIGNATURE` — подпись не прошла проверку.
- `501 / DB_ERROR_USER_LOOKUP` — ошибка БД при чтении пользователя для этой сессии.
- `422 / USER_NOT_FOUND_FOR_SESSION` — пользователь, которому принадлежит сессия, не найден.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 6. Пример ошибки

```json
{
  "op": "SessionLogin",
  "requestId": "slogin-001",
  "status": 403,
  "ok": false,
  "error": "SESSION_KEY_NOT_ACTUAL",
  "message": "session_key не соответствует актуальной версии",
  "payload": {
  }
}
```
