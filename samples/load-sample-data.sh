#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_NAME="test-instance"
CLIENT_TYPE="TEST"
USER="admin"
PASS="admin"

## Register instance
OUTPUT=$(curl --header "Content-Type: application/json" \
  --request POST \
  --user "$USER:$PASS" \
  --silent \
  --data "{\"instanceName\":\"$INSTANCE_NAME\",\"clientType\":\"$CLIENT_TYPE\"}" \
  "$HOST/api/v2/register-instance")

INSTANCE_ID=$(echo $OUTPUT | jq -r ".instanceId")
TOKEN=$(echo $OUTPUT | jq -r ".token")

echo $OUTPUT | jq .
echo "Instance: $INSTANCE_ID"
echo "Token: $TOKEN"

## Upload report
curl --header "Content-Type: application/json" \
  --request POST \
  --user "$INSTANCE_ID:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":600, \"applications\": {\"app1\": 100, \"app2\": 300}}" \
  "$HOST/api/v2/report"

echo ""

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$INSTANCE_ID:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":600, \"applications\": {\"app1\": 200, \"app2\": 300}}" \
  "$HOST/api/v2/report"

echo ""

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$INSTANCE_ID:$TOKEN" \
  --data "{\"instanceId\":\"$INSTANCE_ID\",\"screenTime\":600, \"applications\": {\"app1\": 400, \"app2\": 300}}" \
  "$HOST/api/v2/report"