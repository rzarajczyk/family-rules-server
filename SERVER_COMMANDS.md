# Server-to-Client Command Protocol

## Protocol Goal

Build a general-purpose server-to-client command protocol on top of the existing pull channel:

- delivery from server to client happens in normal client requests, primarily `/api/v2/report`
- commands are durable on the server until acknowledged
- client persists received commands locally before acknowledging them
- command execution and result upload can happen later, independently
- multiple commands can be delivered at once
- commands are identified by unique IDs (`commandId`), not only by name
- command result payload is typed per command via `responseType`

## Important Guarantee

Strict end-to-end "exactly once" is not achievable over an unreliable network if you include arbitrary crashes and local data loss. The realistic and implementable guarantee is:

- exactly once delivery into the client's durable local inbox
- at-most-once acceptance of the final result on the server
- no duplicate execution caused by redelivery, as long as the client persists command state locally and uses `commandId` as the dedupe key

## Protocol Model

Two-phase command lifecycle:

1. Server creates command in persistent storage.
2. Server keeps returning unacknowledged commands in `/api/v2/report` responses.
3. Client persists each command locally in a durable Room inbox table.
4. Client sends an explicit ack after local persistence succeeds.
5. Client executes the command later (never directly from the network response object).
6. Client uploads the typed result later.
7. Server stores the result and marks the command completed.

## Wire Contract

### Capability Advertisement — `client-info` request

Add optional `supportedServerCommands` field to `ClientInfoRequest`:

```json
{
  "version": "1.2.3",
  "timezoneOffsetSeconds": 7200,
  "reportIntervalSeconds": 30,
  "availableStates": [],
  "knownApps": {},
  "supportedServerCommands": ["SEND_LOGS"]
}
```

- old clients omit the field; server treats it as empty list
- GUI disables "Request logs" button when `SEND_LOGS` is not in the list
- server must reject BFF attempts to enqueue a command not supported by the target device

### Command Delivery — `report` response

Extend `ReportResponse` with an optional `serverCommands` field:

```json
{
  "deviceState": "ACTIVE",
  "extra": null,
  "serverCommands": [
    {
      "commandId": "0c5f9d0f-6c96-4f4e-a1fd-c6fef0e54d3b",
      "commandName": "SEND_LOGS",
      "issuedAt": "2026-05-03T12:00:00Z",
      "protocolVersion": 1
    }
  ]
}
```

- field is absent or null when there are no pending commands
- old clients ignore unknown JSON fields (Moshi's `KotlinJsonAdapterFactory` already handles this)
- optionally, also include `serverCommands` in `/api/v2/client-info` response for faster delivery on app startup (v1 optional)

### Ack Endpoint

```
POST /api/v2/command-acks
Auth: Basic <deviceId>:<token>

{
  "acks": [
    {
      "commandId": "0c5f9d0f-6c96-4f4e-a1fd-c6fef0e54d3b",
      "receivedAt": "2026-05-03T12:00:20Z"
    }
  ]
}

Response: { "status": "ok" }
```

- batched to handle multiple commands after coming back online
- must be idempotent: acking the same `commandId` twice returns success

### Result Endpoint

```
POST /api/v2/command-results
Auth: Basic <deviceId>:<token>

{
  "results": [
    {
      "commandId": "0c5f9d0f-6c96-4f4e-a1fd-c6fef0e54d3b",
      "commandName": "SEND_LOGS",
      "completedAt": "2026-05-03T12:01:05Z",
      "status": "SUCCEEDED",
      "responseType": "SEND_LOGS_V1",
      "responsePayload": {
        "logsText": "...",
        "truncated": true,
        "collectedAt": "2026-05-03T12:01:00Z"
      }
    }
  ]
}

Response: { "status": "ok" }
```

- batched
- must be idempotent by `commandId`
- result received before ack: server accepts it and treats it as implicit ack
- result received twice with same payload: success, no-op
- result received twice with conflicting payload: return conflict and log it
- if a command is delivered to a client that does not support it, client should ack it and return `FAILED` with `responseType = "UNSUPPORTED_COMMAND_V1"`

### General Envelope Rules

- `commandId` — stable dedupe key (UUID)
- `commandName` — semantic command name (e.g. `SEND_LOGS`)
- `protocolVersion` — versions the delivery contract
- `responseType` — versions the result schema independently from the command name (e.g. `SEND_LOGS_V1`)
- `responsePayload` — command-specific JSON object
- `status` — at least `SUCCEEDED` or `FAILED`

## Server State Machine

Each command goes through explicit states:

| State | Meaning |
|---|---|
| `QUEUED` | Command created, not yet acknowledged by client |
| `ACKNOWLEDGED` | Client has durably received it |
| `COMPLETED` | Final result stored |
| `FAILED` | Final failure stored |

Completed and failed commands must never be redelivered.

### Command Document Fields

Stored in Firestore sub-collection (see Persistence Model below):

| Field | Type | Notes |
|---|---|---|
| `commandId` | string (UUID) | primary key |
| `deviceId` | string (UUID) | owning device |
| `commandName` | string | e.g. `SEND_LOGS` |
| `status` | string | state machine value |
| `protocolVersion` | int | |
| `createdAt` | timestamp | |
| `lastDeliveredAt` | timestamp | updated on each report delivery |
| `acknowledgedAt` | timestamp | set on ack |
| `completedAt` | timestamp | set on result |
| `deliveryAttempts` | int | counter |
| `resultStatus` | string | `SUCCEEDED` / `FAILED` |
| `responseType` | string | e.g. `SEND_LOGS_V1` |
| `responsePayloadJson` | string | JSON-encoded result payload |

## Server Persistence Model

Do **not** store commands inside the device document. Use a sub-collection per device:

```
/users/{user}/instances/{deviceId}/serverCommands/{commandId}
```

This fits Firestore better for:
- multiple concurrent commands
- command history
- separate result payloads
- future cleanup / TTL

### Firestore Cost Optimization

Because `/api/v2/report` is called every 30 seconds, querying the `serverCommands` sub-collection on every report would be expensive. Add a boolean flag to the device document:

- `hasPendingServerCommands: Boolean` (default `false`)

Rules:
- set to `true` when a command is enqueued
- query the sub-collection during `/report` only when this flag is `true`
- set back to `false` when all commands are acknowledged

### Log Payload Size Cap

Firestore documents have a **1 MiB size limit**. `SEND_LOGS` result payloads must be explicitly capped:

- upload only the **newest ~256 KB** of log content
- set `truncated: true` when the full logs exceeded the cap
- keep the total stored `responsePayloadJson` comfortably below 1 MiB

## Exactly-Once Delivery Mechanics

| Scenario | Behavior |
|---|---|
| Server delivers same command twice | Client upserts by `commandId` — one local row, no duplicate execution |
| Ack is lost in transit | Server redelivers; client sees same `commandId`, does no-op upsert, re-acks |
| Result upload is retried | Server returns success (idempotent by `commandId`), no duplicate result stored |
| Ack received twice | Server returns success, no-op |
| Result received before ack | Server accepts, treats as implicit ack |
| Completed command | Never redelivered |
| Command expired without ack | Server may mark it `FAILED` after a retention period |

## Server Domain Plan

New pieces to add:

- `DeviceCommandsRepository` — port interface
- `DeviceCommandsService` — domain service (enqueue, list pending, ack, store result)
- `FirestoreDeviceCommandsRepository` — Firestore adapter
- Shared command DTOs used by API and BFF controllers
- BFF endpoints for enqueueing and reading command status
- API v2 endpoints for ack and result

Keep command storage entirely separate from `DevicesRepository` — it is a separate concern.

## Android Client Plan

### Changes to existing code

- extend `ClientInfoRequest` with `supportedServerCommands: List<String>?`
- extend `ReportResponseDto` with `serverCommands: List<ServerCommandDto>?`
- add `command-acks` and `command-results` Retrofit endpoints to `FamilyRulesApiService`

### New Android pieces

- **Room entity + DAO** for the local command inbox (primary key: `commandId`)
- **`ServerCommandCoordinator`** — orchestrates the full flow: parse → persist → ack → execute → upload result
- **Handler interface** keyed by `commandName`
- **`SEND_LOGS` handler** — reuses `Logger.exportLogs()` but produces a server-upload result instead of opening a share intent

### Local inbox fields

| Field | Notes |
|---|---|
| `commandId` | primary key |
| `commandName` | |
| `issuedAt` | |
| `receivedAt` | |
| `ackSentAt` | |
| `ackConfirmedAt` | set only after HTTP 200 from ack endpoint |
| `executionState` | `RECEIVED` / `EXECUTING` / `COMPLETED` |
| `resultStatus` | `SUCCEEDED` / `FAILED` |
| `responseType` | |
| `responsePayloadJson` | locally cached result before upload |
| `completedAt` | |

### Client processing flow

For each `/report` response:
1. Parse `serverCommands` (null/absent → empty list).
2. Upsert into local Room inbox in one transaction (uniqueness constraint on `commandId` makes duplicates a no-op).
3. For newly inserted or still-unacked commands, schedule ack retry.
4. For commands not yet executed, schedule execution.
5. **Never execute based only on the network response object.**

Ack flow:
1. Read all locally persisted commands without `ackConfirmedAt`.
2. POST to `/api/v2/command-acks`.
3. Set `ackConfirmedAt` only after HTTP success.

Execution flow:
1. Pick commands with `executionState = RECEIVED`, ordered by `issuedAt`.
2. Mark `EXECUTING`.
3. Dispatch to handler by `commandName`; if no handler, produce `UNSUPPORTED_COMMAND_V1` failure result.
4. Store result locally.
5. Retry POST to `/api/v2/command-results` until HTTP success.
6. Mark local row `COMPLETED`.

Sequential execution for v1, ordered by `issuedAt`.

## BFF / GUI Plan

### New BFF endpoints

```
POST /bff/instance-commands?instanceId=<UUID>
Body:  { "commandName": "SEND_LOGS" }
Response: { "commandId": "UUID" }
```

- validates device belongs to authenticated user
- validates `commandName` is in device's `supportedServerCommands`; returns 422 if not
- generates `commandId`, stores command in `QUEUED` state, sets `hasPendingServerCommands = true`

```
GET /bff/instance-commands/{commandId}
Response (pending):   { "status": "QUEUED" | "ACKNOWLEDGED" }
Response (completed): { "status": "COMPLETED", "commandName": "SEND_LOGS", "resultStatus": "SUCCEEDED", "responseType": "SEND_LOGS_V1", "responsePayload": { ... } }
Response (failed):    { "status": "FAILED", "commandName": "SEND_LOGS", "resultStatus": "FAILED", "responseType": "...", "responsePayload": { ... } }
Response (not found): 404
```

### GUI flow

1. User clicks "Request Logs" button (enabled only when `SEND_LOGS` is in device's `supportedServerCommands`).
2. BFF creates command, returns `commandId`.
3. GUI opens waiting modal / spinner.
4. GUI polls `GET /bff/instance-commands/{commandId}` every few seconds.
5. On `COMPLETED`, GUI closes spinner and shows popup with log text.
6. On `FAILED`, GUI shows error message.

Expose `supportedServerCommands` in the existing instance info or status DTO so the GUI can enable/disable the button without an extra request.

## New API Endpoints Summary

| Direction | Method | Path | Purpose |
|---|---|---|---|
| GUI → Server | `POST` | `/bff/instance-commands?instanceId=` | Issue a command |
| GUI → Server | `GET` | `/bff/instance-commands/{commandId}` | Poll for status and result |
| Server → Client | _(in report response)_ | `/api/v2/report` | Deliver pending commands |
| Client → Server | `POST` | `/api/v2/command-acks` | Acknowledge receipt |
| Client → Server | `POST` | `/api/v2/command-results` | Submit result |

## Changed Data Structures Summary

| Location | Change |
|---|---|
| `ClientInfoRequest` (client + server) | Add `supportedServerCommands: List<String>?` |
| Device document in Firestore | Add `hasPendingServerCommands: Boolean` and `supportedServerCommands` (JSON) |
| `ReportResponse` (server) / `ReportResponseDto` (client) | Add `serverCommands: List<ServerCommandDto>?` |
| Firestore (new sub-collection) | `/users/{user}/instances/{deviceId}/serverCommands/{commandId}` |
| Android (new Room table) | Local command inbox |

## Result Schema Examples

### `SEND_LOGS_V1`

```json
{
  "responseType": "SEND_LOGS_V1",
  "responsePayload": {
    "logsText": "...",
    "truncated": false,
    "collectedAt": "2026-05-03T12:01:00Z"
  }
}
```

### `UNSUPPORTED_COMMAND_V1` (generic failure for unknown commands)

```json
{
  "responseType": "UNSUPPORTED_COMMAND_V1",
  "responsePayload": {
    "receivedCommandName": "SOME_FUTURE_COMMAND"
  }
}
```

## Tests To Add

### Server integration tests

- `/client-info` stores `supportedServerCommands` on device document
- `/report` includes queued commands in response
- acknowledged commands stop appearing in `/report` response
- duplicate ack is idempotent
- duplicate result upload is idempotent
- result received before ack is accepted (implicit ack)
- completed commands are not re-delivered
- BFF rejects command creation when command not in device's `supportedServerCommands`
- `hasPendingServerCommands` flag is set on enqueue and cleared when all commands acked

### Android tests

- duplicate delivery stores one local command row
- ack is sent only after local persistence succeeds
- service restart resumes pending commands from local Room DB
- result upload retries after transient failure
- duplicate server redelivery does not trigger duplicate execution
- unknown `commandName` produces `UNSUPPORTED_COMMAND_V1` failure result

### GUI tests

- button disabled when `SEND_LOGS` not in `supportedServerCommands`
- waiting modal polls until status is `COMPLETED`
- popup displays returned log text
- error state shown on `FAILED` result

## Implementation Order

1. Add `supportedServerCommands` to `client-info` request/response (server + client).
2. Add server command persistence: `DeviceCommandsRepository` port + Firestore adapter + `hasPendingServerCommands` flag.
3. Add `DeviceCommandsService` domain service.
4. Extend `/report` response with `serverCommands`; query sub-collection only when flag is set.
5. Add `/api/v2/command-acks` endpoint.
6. Add `/api/v2/command-results` endpoint.
7. Add Android Room inbox entity + DAO.
8. Add `ServerCommandCoordinator` and handler interface.
9. Implement `SEND_LOGS` handler.
10. Add BFF endpoints and GUI button/modal.
11. Add cleanup/retention policy for old commands and results.

## Open Questions

1. **Log size cap**: Always cap at newest ~256 KB with `truncated` flag, or send full logs when they fit within the cap? Recommended: always apply the cap for predictability.
2. **Command TTL / cleanup**: How long to retain completed or failed commands in Firestore? Suggested: 7 days, or until GUI explicitly dismisses the result.
3. **`client-info` response also carrying `serverCommands`**: Include for faster startup delivery, or rely solely on the report channel? Suggested: defer to v2, report channel is sufficient for v1.
