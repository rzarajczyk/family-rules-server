#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_ID="110a1314-6c28-4aa3-817c-c5c88220236f"
TOKEN="8278f625-fdc8-46b9-b548-d066a57073e0"

curl --header "Content-Type: application/json" \
  --request POST \
  --verbose \
  --user "$INSTANCE_ID:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":600, \"applications\": {\"app1\": 400, \"app2\": 300}}" \
  "$HOST/api/v2/report"