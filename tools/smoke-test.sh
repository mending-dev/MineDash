#!/usr/bin/env bash
# Smoke test for a running MineDash server.
# Usage:   tools/smoke-test.sh <base-url> <username> <password>
# Example: tools/smoke-test.sh http://localhost:8765 admin 'my-password'
# Needs the console module to be enabled. With "secureCookie": true the base URL must be https.

set -u

BASE="${1:-}"
LOGIN_USER="${2:-}"
LOGIN_PASS="${3:-}"

if [ -z "$BASE" ] || [ -z "$LOGIN_USER" ] || [ -z "$LOGIN_PASS" ]; then
  echo "Usage: $0 <base-url> <username> <password>"
  exit 2
fi

BASE="${BASE%/}"
COOKIES="$(mktemp)"
trap 'rm -f "$COOKIES"' EXIT
FAILED=0

pass() { printf '  PASS  %s\n' "$1"; }
fail() { printf '  FAIL  %s\n' "$1"; FAILED=$((FAILED + 1)); }

json_escape() { printf '%s' "$1" | sed -e 's/\\/\\\\/g' -e 's/"/\\"/g'; }

# check_status <expected-code> <description> <curl arguments...>
check_status() {
  local expected="$1" description="$2"
  shift 2
  local actual
  actual="$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$@")"
  if [ "$actual" = "$expected" ]; then
    pass "$description"
  else
    fail "$description (expected $expected, got $actual)"
  fi
}

# check_body <regex> <description> <curl arguments...>
check_body() {
  local pattern="$1" description="$2"
  shift 2
  local body
  body="$(curl -s --max-time 10 "$@")"
  if printf '%s' "$body" | grep -Eq "$pattern"; then
    pass "$description"
  else
    fail "$description (not found: $pattern)"
  fi
}

LOGIN_BODY="{\"username\":\"$(json_escape "$LOGIN_USER")\",\"password\":\"$(json_escape "$LOGIN_PASS")\"}"

echo "MineDash smoke test: $BASE"

echo "Access control"
check_status 401 "API rejects anonymous requests" "$BASE/api/auth/me"
check_status 401 "Wrong password is rejected" -X POST -H 'Content-Type: application/json' \
  -d '{"username":"nobody","password":"wrong"}' "$BASE/api/auth/login"
check_status 403 "Cross-origin POST is blocked" -X POST -H 'Origin: https://evil.example' \
  -H 'Content-Type: application/json' -d "$LOGIN_BODY" "$BASE/api/auth/login"

echo "Frontend and headers"
check_body '<div id="app">' "Panel HTML is served" "$BASE/"
HEADERS="$(curl -s -D - -o /dev/null --max-time 10 "$BASE/")"
for header in content-security-policy x-content-type-options x-frame-options referrer-policy; do
  if printf '%s' "$HEADERS" | grep -qi "^$header:"; then
    pass "Header $header is set"
  else
    fail "Header $header is missing"
  fi
done

echo "Session"
check_status 200 "Login works" -c "$COOKIES" -X POST -H 'Content-Type: application/json' \
  -d "$LOGIN_BODY" "$BASE/api/auth/login"
check_status 200 "Session is valid" -b "$COOKIES" "$BASE/api/auth/me"

echo "Overview"
check_body '"software"' "Server info is available" -b "$COOKIES" "$BASE/api/core/overview/info"
check_body '"samples":\[' "Metrics are available" -b "$COOKIES" "$BASE/api/core/overview/metrics?since=0"

echo "Modules"
check_body '"id":"console".*"state":"enabled"' "Console module is enabled" -b "$COOKIES" "$BASE/api/core/modules"
check_status 204 "Command can be sent" -b "$COOKIES" -X POST -H 'Content-Type: application/json' \
  -d '{"command":"list"}' "$BASE/api/modules/console/command"

echo "Logout"
check_status 204 "Logout works" -b "$COOKIES" -c "$COOKIES" -X POST "$BASE/api/auth/logout"
check_status 401 "Session is gone after logout" -b "$COOKIES" "$BASE/api/auth/me"

echo
if [ "$FAILED" -eq 0 ]; then
  echo "All checks passed."
else
  echo "$FAILED check(s) failed."
  exit 1
fi