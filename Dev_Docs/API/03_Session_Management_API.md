# API для разработчиков: Управление сессиями

Этот файл описывает методы, которые используются уже после успешной авторизации пользователя в сессию.

Здесь два метода:

- `ListSessions` — получить список активных сессий пользователя;
- `CloseActiveSession` — закрыть одну из активных сессий.

Логика раздела такая:

- сначала пользователь проходит `SessionLogin`;
- после этого сервер считает соединение авторизованным;
- уже в этом состоянии клиент может читать список сессий и управлять ими.

То есть это не этап создания или входа в сессию, а этап последующего контроля уже существующих активных сессий.

## 1. `ListSessions`

Доступно только после успешного `SessionLogin`.

### Запрос

```json
{
  "op": "ListSessions",
  "requestId": "list-001",
  "payload": {
  }
}
```

### Успешный ответ

```json
{
  "op": "ListSessions",
  "requestId": "list-001",
  "status": 200,
  "ok": true,
  "payload": {
    "sessions": [
      {
        "sessionId": "sess_7c5e5c4b",
        "clientInfoFromClient": "Android 15; Pixel 9",
        "clientInfoFromRequest": "UA=Java-http-client/17.0.18; remote=127.0.0.1",
        "geo": "RU/Moscow",
        "lastAuthenticatedAtMs": 1774600010500
      }
    ]
  }
}
```

### Специфические коды ошибок `ListSessions`

- `422 / NOT_AUTHENTICATED` — запрос доступен только после успешного `SessionLogin`.
- `501 / DB_ERROR_LIST_SESSIONS` — ошибка БД при чтении списка активных сессий.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 2. `CloseActiveSession`

Доступно только после успешного `SessionLogin`.

### Запрос

```json
{
  "op": "CloseActiveSession",
  "requestId": "close-001",
  "payload": {
    "sessionId": "sess_7c5e5c4b"
  }
}
```

### Успешный ответ

```json
{
  "op": "CloseActiveSession",
  "requestId": "close-001",
  "status": 200,
  "ok": true,
  "payload": {
  }
}
```

### Специфические коды ошибок `CloseActiveSession`

- `422 / NOT_AUTHENTICATED` — запрос доступен только после успешного `SessionLogin`.
- `400 / NO_SESSION_TO_CLOSE` — сервер не смог определить, какую сессию нужно закрыть.
- `501 / DB_ERROR` — ошибка БД при поиске сессии или её удалении.
- `422 / SESSION_NOT_FOUND` — целевая сессия не найдена.
- `422 / SESSION_OF_ANOTHER_USER` — нельзя закрывать сессию другого пользователя.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 3. Пример ошибки

```json
{
  "op": "CloseActiveSession",
  "requestId": "close-001",
  "status": 403,
  "ok": false,
  "error": "NOT_AUTHENTICATED",
  "message": "Операция доступна только для авторизованных пользователей",
  "payload": {
  }
}
```
