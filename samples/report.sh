#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_ID="c7aaca32-988c-455e-8a39-89668d79628"
USER="admin"
TOKEN="f6dbd5e1-c8f6-4d57-b8ac-5ce1d81110ff"

curl --header "Content-Type: application/json" \
  --request POST \
  --verbose \
  --user "$USER:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":10, \"applications\": {}}" \
  "$HOST/api/v2/report"