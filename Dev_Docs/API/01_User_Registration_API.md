# API для разработчиков: Регистрация пользователя

Этот файл описывает временный раздел API, связанный с заведением пользователя на сервере и проверкой, существует ли пользователь.

Сейчас здесь два метода:

- `AddUser` — временная серверная регистрация пользователя;
- `GetUser` — временная серверная проверка существования пользователя и чтение его базовых данных.

Их логика пока вспомогательная и dev-oriented: сервер сам хранит эти данные локально и сам отвечает на existence-check. В будущем оба сценария должны быть заменены на нормальную работу напрямую через Solana, но пока этот контракт нужен клиентам для разработки и интеграции.

## Статус документа

Это временная глава API.

Текущая регистрация пользователя и текущая проверка, существует пользователь или нет, пока реализованы как серверные dev/test операции. В будущем и регистрация, и проверка identity должны идти напрямую через Solana.

---

## 1. Операция `AddUser`

### Назначение

Временная регистрация локального пользователя на сервере.

Сервер:

- создаёт запись в `solana_users`;
- создаёт стартовое состояние в `blockchain_state`.

### Запрос

```json
{
  "op": "AddUser",
  "requestId": "reg-001",
  "payload": {
    "login": "anya",
    "blockchainName": "anya-001",
    "solanaKey": "BASE64_32_PUBLIC_KEY",
    "blockchainKey": "BASE64_32_PUBLIC_KEY",
    "deviceKey": "BASE64_32_PUBLIC_KEY",
    "bchLimit": 1000000
  }
}
```

### Успешный ответ

```json
{
  "op": "AddUser",
  "requestId": "reg-001",
  "status": 200,
  "ok": true,
  "payload": {
  }
}
```

### Пример ошибки

```json
{
  "op": "AddUser",
  "requestId": "reg-001",
  "status": 409,
  "ok": false,
  "error": "USER_ALREADY_EXISTS",
  "message": "Пользователь с таким login уже существует",
  "payload": {
  }
}
```

### Специфические коды ошибок `AddUser`

- `400 / BAD_FIELDS` — не переданы обязательные поля регистрации.
- `400 / BAD_BLOCKCHAIN_NAME` — `blockchainName` не соответствует формату `<login>-NNN`.
- `400 / BAD_KEY_FORMAT` — один из ключей не является корректным `Base64(32 bytes)`.
- `409 / USER_ALREADY_EXISTS` — пользователь с таким `login` уже есть.
- `409 / BLOCKCHAIN_ALREADY_EXISTS` — такой `blockchainName` уже занят.
- `409 / BLOCKCHAIN_STATE_ALREADY_EXISTS` — стартовое состояние blockchain уже существует.
- `501 / DB_ERROR` — ошибка БД при создании пользователя.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 2. Операция `GetUser`

### Назначение

Временная серверная проверка, существует пользователь или нет.

Важно:

- это временное решение;
- позже клиент должен проверять existence/identity напрямую через Solana;
- на финальный production flow не стоит жёстко завязывать архитектуру клиента на `GetUser`.

### Запрос

```json
{
  "op": "GetUser",
  "requestId": "user-001",
  "payload": {
    "login": "anya"
  }
}
```

### Успешный ответ: пользователь существует

```json
{
  "op": "GetUser",
  "requestId": "user-001",
  "status": 200,
  "ok": true,
  "payload": {
    "exists": true,
    "login": "Anya",
    "blockchainName": "anya-001",
    "solanaKey": "BASE64_32_PUBLIC_KEY",
    "blockchainKey": "BASE64_32_PUBLIC_KEY",
    "deviceKey": "BASE64_32_PUBLIC_KEY"
  }
}
```

### Успешный ответ: пользователя нет

```json
{
  "op": "GetUser",
  "requestId": "user-001",
  "status": 200,
  "ok": true,
  "payload": {
    "exists": false
  }
}
```

### Пример ошибки

```json
{
  "op": "GetUser",
  "requestId": "user-001",
  "status": 400,
  "ok": false,
  "error": "BAD_FIELDS",
  "message": "Некорректные поля: login",
  "payload": {
  }
}
```

### Специфические коды ошибок `GetUser`

- `400 / BAD_FIELDS` — не передан или пуст `login`.
- `501 / DB_ERROR` — ошибка БД при поиске пользователя.
- `500 / INTERNAL_ERROR` — непредвиденная внутренняя ошибка сервера.

---

## 3. Короткое резюме

- `AddUser` — временная регистрация пользователя на сервере.
- `GetUser` — временная проверка существования пользователя на сервере.
- И регистрация, и existence-check позже должны быть переведены на Solana.
