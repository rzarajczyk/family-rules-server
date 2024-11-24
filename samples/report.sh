#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_ID=""
USER="admin"
TOKEN=""

curl --header "Content-Type: application/json" \
  --request POST \
  --verbose \
  --user "$USER:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":0, \"applications\": {}}" \
  "$HOST/api/v1/report"