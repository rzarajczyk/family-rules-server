#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_ID="c0604090-a436-4c1b-9984-d25dde6240d"
TOKEN="991a9723-9f71-4d62-bad7-f63fccd543ff"

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$INSTANCE_ID:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":600, \"applications\": {\"app1\": 400, \"app2\": 300}}" \
  "$HOST/api/v2/report"