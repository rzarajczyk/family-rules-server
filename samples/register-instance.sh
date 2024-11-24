#!/bin/bash
HOST="http://localhost:8080"
INSTANCE_NAME="sony-tv"
CLIENT_TYPE="ANDROID_TV"
USER=""
PASS=""

curl --header "Content-Type: application/json" \
  --request POST \
  --user "$USER:$PASS" \
  --data "{\"instanceName\":\"$INSTANCE_NAME\",\"clientType\":\"$CLIENT_TYPE\"}" \
  "$HOST/api/v1/register-instance"