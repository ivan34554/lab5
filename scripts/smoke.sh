#!/bin/bash
BASE="${BASE:-http://localhost:8080}"
JAR="$(mktemp)"
trap 'rm -f "$JAR"' EXIT

TOKEN="$(curl -s -c "$JAR" -b "$JAR" "$BASE/api/auth/csrf" | sed -n 's/.*"token":"\([^"]*\)".*/\1/p')"

call() {
  local user="$1" method="$2" path="$3" body="$4"
  echo; echo "### ${user:-anonymous} $method $path"
  curl -s -b "$JAR" ${user:+-u "$user"} -X "$method" "$BASE$path" \
    -H 'Content-Type: application/json' -H "X-XSRF-TOKEN: $TOKEN" \
    ${body:+-d "$body"} -w '\nHTTP %{http_code}\n'
}

IVAN='ivan:Ivan#Pass2026'
ANNA='anna:Anna#Pass2026'
MODERATOR='moderator:Moder#Pass2026'
ADMIN='admin:Admin#Pass2026'
PETR='petr:Petr#Pass2026'

call ''          GET  /api/podcasts
call "$PETR"     GET  /api/auth/me
call "$IVAN"     GET  /api/auth/me
call "$IVAN"     GET  /api/users
call "$ADMIN"    GET  /api/users
call "$ANNA"     PATCH /api/podcasts/1 '{"title":"Чужой подкаст"}'
call "$IVAN"     POST /api/episodes/4/publish-now
call "$IVAN"     POST /api/audio-files/4/make-main
call "$ANNA"     POST /api/podcasts/1/archive
call "$MODERATOR" POST /api/podcasts/1/archive
call "$IVAN"     POST /api/podcasts/1/restore

echo; echo "### JWT: login"
LOGIN="$(curl -s -X POST "$BASE/auth/login" -H 'Content-Type: application/json' \
  -d '{"username":"ivan","password":"Ivan#Pass2026","deviceId":"smoke"}')"
echo "$LOGIN"
ACCESS="$(echo "$LOGIN" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
REFRESH="$(echo "$LOGIN" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')"

echo; echo "### JWT: GET /api/auth/me with access token"
curl -s -H "Authorization: Bearer $ACCESS" "$BASE/api/auth/me" -w '\nHTTP %{http_code}\n'

echo; echo "### JWT: refresh"
curl -s -X POST "$BASE/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" -w '\nHTTP %{http_code}\n'

echo; echo "### JWT: the same refresh again (expected 401)"
curl -s -X POST "$BASE/auth/refresh" -H 'Content-Type: application/json' \
  -d "{\"refreshToken\":\"$REFRESH\"}" -w '\nHTTP %{http_code}\n'

