# 06. Channels Read API

## Человеко-читаемое объяснение
Эти 3 функции — это **чтение данных каналов** для UI:

1. `ListSubscriptionsFeed` — отдает данные для экрана списка каналов:
   - ваши каналы (личный + созданные вами),
   - каналы пользователей, на кого вы подписаны,
   - отдельные каналы, на которые вы подписаны напрямую.

2. `GetChannelMessages` — отдает полную ленту одного канала (пока без курсоров, загружается сразу целиком),
   включая версии сообщений, лайки и ответы.

3. `GetMessageThread` — отдает дерево обсуждения вокруг конкретного сообщения:
   предки, фокус-сообщение, потомки.

> На первом этапе мы **не используем курсоры** (`nextCursor`) и загружаем полные списки.

---

## 1) ListSubscriptionsFeed

### Request
```json
{
  "op": "ListSubscriptionsFeed",
  "requestId": "req-1",
  "payload": {
    "login": "Alice",
    "limit": 200
  }
}
```

### Response (success)
```json
{
  "op": "ListSubscriptionsFeed",
  "requestId": "req-1",
  "status": 200,
  "ok": true,
  "payload": {
    "login": "Alice",
    "ownedChannels": [
      {
        "channel": {
          "ownerLogin": "Alice",
          "ownerBlockchainName": "alice-001",
          "channelName": "0",
          "personal": true,
          "channelRoot": { "blockNumber": 0, "blockHash": "..." }
        },
        "messagesCount": 120,
        "lastMessage": {
          "messageRef": { "blockNumber": 921, "blockHash": "..." },
          "text": "последняя версия текста",
          "createdAtMs": 1760000000000,
          "authorLogin": "Alice",
          "authorBlockchainName": "alice-001"
        }
      }
    ],
    "followedUsersChannels": [
      {
        "channel": {
          "ownerLogin": "Bob",
          "ownerBlockchainName": "bob-001",
          "channelName": "0",
          "personal": true,
          "channelRoot": { "blockNumber": 0, "blockHash": "..." }
        },
        "messagesCount": 540,
        "lastMessage": {
          "messageRef": { "blockNumber": 922, "blockHash": "..." },
          "text": "последняя версия текста",
          "createdAtMs": 1760000100000,
          "authorLogin": "Bob",
          "authorBlockchainName": "bob-001"
        }
      }
    ],
    "followedChannels": [
      {
        "channel": {
          "ownerLogin": "Carl",
          "ownerBlockchainName": "carl-001",
          "channelName": "market",
          "personal": false,
          "channelRoot": { "blockNumber": 456, "blockHash": "..." }
        },
        "messagesCount": 90,
        "lastMessage": {
          "messageRef": { "blockNumber": 1002, "blockHash": "..." },
          "text": "актуальный текст",
          "createdAtMs": 1760001000000,
          "authorLogin": "Carl",
          "authorBlockchainName": "carl-001"
        }
      }
    ]
  }
}
```

---

## 2) GetChannelMessages

### Request
```json
{
  "op": "GetChannelMessages",
  "requestId": "req-2",
  "payload": {
    "channel": {
      "ownerBlockchainName": "bob-001",
      "channelRootBlockNumber": 123,
      "channelRootBlockHash": "..."
    },
    "limit": 200,
    "sort": "asc"
  }
}
```

### Response (success)
```json
{
  "op": "GetChannelMessages",
  "requestId": "req-2",
  "status": 200,
  "ok": true,
  "payload": {
    "channel": {
      "ownerLogin": "Bob",
      "ownerBlockchainName": "bob-001",
      "channelName": "news",
      "channelRoot": { "blockNumber": 123, "blockHash": "..." }
    },
    "messages": [
      {
        "messageRef": { "blockNumber": 140, "blockHash": "..." },
        "authorLogin": "Bob",
        "authorBlockchainName": "bob-001",
        "createdAtMs": 1760000000000,
        "text": "текущая версия",
        "likesCount": 12,
        "repliesCount": 3,
        "versionsTotal": 4,
        "versions": [
          { "versionIndex": 1, "blockNumber": 140, "blockHash": "...", "text": "v1", "createdAtMs": 1760000000000 },
          { "versionIndex": 2, "blockNumber": 155, "blockHash": "...", "text": "v2", "createdAtMs": 1760001000000 },
          { "versionIndex": 3, "blockNumber": 170, "blockHash": "...", "text": "v3", "createdAtMs": 1760002000000 },
          { "versionIndex": 4, "blockNumber": 199, "blockHash": "...", "text": "v4", "createdAtMs": 1760003000000 }
        ]
      }
    ]
  }
}
```

---

## 3) GetMessageThread

### Request
```json
{
  "op": "GetMessageThread",
  "requestId": "req-3",
  "payload": {
    "message": {
      "blockchainName": "bob-001",
      "blockNumber": 333,
      "blockHash": "..."
    },
    "depthUp": 20,
    "depthDown": 2,
    "limitChildrenPerNode": 50
  }
}
```

### Response (success)
```json
{
  "op": "GetMessageThread",
  "requestId": "req-3",
  "status": 200,
  "ok": true,
  "payload": {
    "ancestors": [MessageNode],
    "focus": MessageNode,
    "descendants": [MessageNodeTree]
  }
}
```

---

## Reason codes
- `bad_fields`
- `user_not_found`
- `channel_not_found`
- `message_not_found`
- `limit_too_large`
- `channel_name_already_exists`
- `internal_error`
