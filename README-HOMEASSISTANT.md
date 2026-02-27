# HomeAssistant Integration Manual

This document explains how to connect Family Rules to HomeAssistant (HA). There are two complementary mechanisms:

| Mechanism | Direction | Purpose |
|---|---|---|
| **Webhook** | Family Rules → HA | Push usage data and group info to HA whenever device activity changes |
| **Integration API** | HA → Family Rules | Let HA read group states and apply named states (e.g., lock/unlock a group of apps) |

Both are optional and independent. You can use either one alone, or both together.

---

## Concepts

### App Groups

An **app group** is a named collection of apps across one or more devices (e.g., *Games*, *Social Media*). Each group has a stable UUID `id` that never changes — safe to use in HA automations.

### Group States

A **group state** is a named preset for a group (e.g., *Locked*, *Allowed*, *Homework Mode*). Applying a state forces every device in the group into a defined mode. You create and name states in the Family Rules UI under **Groups → Define Group States**.

Each state also has a stable UUID `id`. The three possible values of `currentState.kind` are:

| Kind | Meaning |
|---|---|
| `automatic` | No forced state is active — devices follow their normal schedule |
| `named` | Every device in the group matches a defined named state |
| `different` | Devices have mixed forced states that don't correspond to any named state |

---

## Part 1 — Outbound Webhook (Family Rules → HA)

### What the webhook does

Family Rules sends a JSON **POST** request to a URL you configure whenever device activity data changes. You handle this in HA using a **Webhook trigger** or a **RESTful command** — the server doesn't care how HA consumes it.

### How often it fires

- Every **30 seconds** while any device has been recently active.
- At **00:01 every night** to reset daily screen-time counters.
- **Immediately** after a state is applied via the Integration API.

### Setup in Family Rules

1. Open **Settings** in the Family Rules web UI.
2. Scroll to **HomeAssistant Webhook Integration**.
3. Check **Enable sending updates via webhook**.
4. Paste your HA webhook URL into the **Webhook URL** field.  
   Typically this looks like `https://<your-ha-host>/api/webhook/<webhook-id>`.
5. Click **Save Webhook Settings**.

To verify it's working, click **Last Calls** to see a history of recent webhook deliveries with their HTTP status codes and full request payloads.

### Webhook payload format

```json
{
  "date": "2026-02-27",
  "devices": [],
  "appGroups": [
    {
      "id": "a1b2c3d4-1234-5678-abcd-ef0123456789",
      "name": "Games",
      "color": "#FF5733",
      "appsCount": 5,
      "devicesCount": 2,
      "totalScreenTime": 3600,
      "online": true,
      "availableStates": [
        { "id": "state-uuid-1", "name": "Locked" },
        { "id": "state-uuid-2", "name": "Allowed" }
      ]
    }
  ]
}
```

| Field | Type | Description |
|---|---|---|
| `date` | string | Today's local date (`YYYY-MM-DD`) |
| `devices` | array | Always empty (reserved for future use) |
| `appGroups` | array | One object per app group |
| `appGroups[].id` | string (UUID) | Stable group identifier — use this in Integration API calls |
| `appGroups[].name` | string | Group name |
| `appGroups[].color` | string | Hex colour of the group |
| `appGroups[].appsCount` | number | Total number of app slots in the group |
| `appGroups[].devicesCount` | number | Number of devices covered by the group |
| `appGroups[].totalScreenTime` | number | Today's total screen time in **seconds** |
| `appGroups[].online` | boolean | `true` if any app in the group is currently active |
| `appGroups[].availableStates` | array | Named states defined for this group |
| `appGroups[].availableStates[].id` | string (UUID) | Stable state identifier — use this in `apply-state` calls |
| `appGroups[].availableStates[].name` | string | State label |

> **Note:** The webhook payload does **not** include `currentState`. To know which named state is currently active after a push, call `GET /integration-api/v1/app-groups/{groupId}/state` (see Part 2).

### HA configuration example (webhook automation)

In HA, create a **Webhook automation** that stores the payload in input helpers or template sensors:

```yaml
automation:
  - alias: "Family Rules webhook received"
    trigger:
      - platform: webhook
        webhook_id: your-webhook-id
    action:
      - service: input_number.set_value
        target:
          entity_id: input_number.games_screen_time
        data:
          value: "{{ trigger.json.appGroups | selectattr('name', 'eq', 'Games') | map(attribute='totalScreenTime') | first }}"
```

---

## Part 2 — Integration API (HA → Family Rules)

### What the Integration API does

The Integration API lets HA **read** the current group state and **apply** named states. This enables automations like:

- "When school starts, lock the Games group."
- "On weekends at noon, switch Games to Allowed."
- "Show the current state of Games on a dashboard tile."

### Authentication

All Integration API requests require a **Bearer token** in the `Authorization` header:

```
Authorization: Bearer xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx
```

Tokens are scoped to a single user — they cannot access another user's data. Each request is stateless; no session cookies are involved.

### Generating a token

1. Open **Settings** in the Family Rules web UI.
2. Scroll to **HomeAssistant Integration API** (at the top of the page).
3. Click **Generate / Regenerate Token**.
4. Copy the token immediately — it is shown in full only once.
5. Store it in HA as a secret (e.g., in `secrets.yaml` as `family_rules_token`).

To invalidate a token (e.g., if it leaks), click **Revoke Token**, then generate a new one.

> **Security:** Treat the token like a password. Anyone who has it can read and change device states for your account.

---

### Endpoint reference

Base URL: `https://<your-family-rules-host>/integration-api/v1`

All endpoints return `401 Unauthorized` when the token is missing or invalid, and `404 Not Found` when a group or state ID does not exist (or belongs to a different user).

---

#### `GET /integration-api/v1/app-groups`

Returns all app groups with their available states and computed current state.

**Request:**
```http
GET /integration-api/v1/app-groups
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
{
  "appGroups": [
    {
      "id": "a1b2c3d4-1234-5678-abcd-ef0123456789",
      "name": "Games",
      "color": "#FF5733",
      "availableStates": [
        { "id": "state-uuid-1", "name": "Locked" },
        { "id": "state-uuid-2", "name": "Allowed" }
      ],
      "currentState": {
        "kind": "automatic",
        "label": "Automatic",
        "stateId": null
      }
    }
  ]
}
```

Use this endpoint once during setup to discover the group IDs and state IDs to hardcode in your HA configuration.

---

#### `GET /integration-api/v1/app-groups/{groupId}/state`

Returns the current state of a single group. Lightweight — good for polling or checking state in a condition.

**Request:**
```http
GET /integration-api/v1/app-groups/a1b2c3d4-1234-5678-abcd-ef0123456789/state
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
{
  "groupId": "a1b2c3d4-1234-5678-abcd-ef0123456789",
  "currentState": {
    "kind": "named",
    "label": "Locked",
    "stateId": "state-uuid-1"
  },
  "availableStates": [
    { "id": "state-uuid-1", "name": "Locked" },
    { "id": "state-uuid-2", "name": "Allowed" }
  ]
}
```

---

#### `POST /integration-api/v1/app-groups/{groupId}/apply-state/{stateId}`

Applies a named state to a group. Forces every device referenced by the state into the configured mode. Also immediately triggers a webhook push so HA receives the updated state without waiting for the next scheduler tick.

No request body.

**Request:**
```http
POST /integration-api/v1/app-groups/a1b2c3d4-1234-5678-abcd-ef0123456789/apply-state/state-uuid-1
Authorization: Bearer <token>
```

**Response `200 OK`:**
```json
{
  "success": true,
  "appliedStateName": "Locked",
  "appliedDevices": 2
}
```

| Field | Type | Description |
|---|---|---|
| `success` | boolean | Always `true` on a 200 response |
| `appliedStateName` | string | The name of the state that was applied |
| `appliedDevices` | number | How many devices were updated |

---

### HA configuration example

#### 1. Store IDs and token

First, use a tool like `curl` or the HA **RESTful** integration to call `GET /app-groups` once and note your group IDs and state IDs.

Then in `secrets.yaml`:
```yaml
family_rules_token: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx"
```

And in `configuration.yaml`, create input helpers to track the current state:
```yaml
input_select:
  games_state:
    name: Games group state
    options:
      - Automatic
      - Locked
      - Allowed
      - Different
    initial: Automatic
```

#### 2. Read state (REST sensor)

```yaml
sensor:
  - platform: rest
    name: "Games current state"
    resource: "https://<your-host>/integration-api/v1/app-groups/a1b2c3d4-.../state"
    headers:
      Authorization: "Bearer !secret family_rules_token"
    value_template: "{{ value_json.currentState.label }}"
    json_attributes:
      - currentState
    scan_interval: 60
```

#### 3. Apply a state (REST command)

```yaml
rest_command:
  lock_games:
    url: "https://<your-host>/integration-api/v1/app-groups/a1b2c3d4-.../apply-state/state-uuid-1"
    method: POST
    headers:
      Authorization: "Bearer !secret family_rules_token"

  allow_games:
    url: "https://<your-host>/integration-api/v1/app-groups/a1b2c3d4-.../apply-state/state-uuid-2"
    method: POST
    headers:
      Authorization: "Bearer !secret family_rules_token"
```

#### 4. Automation: lock at school time

```yaml
automation:
  - alias: "Lock Games on school days"
    trigger:
      - platform: time
        at: "07:30:00"
    condition:
      - condition: time
        weekday: [mon, tue, wed, thu, fri]
    action:
      - service: rest_command.lock_games
```

#### 5. Dashboard button card

```yaml
type: button
name: Lock Games
tap_action:
  action: call-service
  service: rest_command.lock_games
icon: mdi:lock
```

---

## Combining Both Mechanisms

The most powerful setup uses both together:

1. HA sends `POST /apply-state/...` to lock or unlock a group.
2. The server immediately fires a webhook back to HA.
3. HA receives the webhook, reads the updated `totalScreenTime` and updates dashboard sensors.

This creates a **request → confirm** loop: HA issues a command and receives independent confirmation that the state actually changed, without relying on the 200 response alone.

```
HA automation
    │  POST /apply-state/locked
    ▼
Family Rules server
    │  sets forcedDeviceState on each device
    │  enqueues webhook
    ▼
Webhook fires to HA
    │  payload contains updated appGroups data
    ▼
HA webhook automation updates sensors/input_selects
```

---

## Discovering Group and State IDs

You only need to do this once. Run the following from any terminal (replace placeholders):

```bash
curl -s \
  -H "Authorization: Bearer <your-token>" \
  https://<your-host>/integration-api/v1/app-groups \
  | python3 -m json.tool
```

The response lists every group with its `id` and every state with its `id`. Copy those values into your HA `configuration.yaml`.

IDs are stable — they do not change when you rename a group or state, and they survive server restarts and database migrations.
