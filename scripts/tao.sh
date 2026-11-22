#!/usr/bin/env bash
#
# Reads and updates a manually maintained portfolio holding (TAO staking by default).
#
# Why this script exists: the write path /api/portfolio/manual-holdings sits behind its OWN
# Cloudflare Access application, so a browser session minted at the root is refused there however
# fresh it is. The credential that works is the service token `tradeengine-staking`. See the
# "Two Access applications, two sessions" section in CLAUDE.md.
#
# Usage:
#   scripts/tao.sh                      # show current amounts, change nothing
#   scripts/tao.sh 15.8678              # set TAO staking, asking first
#   scripts/tao.sh 19.90 --key trb-staking
#   scripts/tao.sh 15.8678 --snapshot   # also force a snapshot, so it lands on the chart at once
#   scripts/tao.sh 15.8678 -y           # no confirmation prompt
#
# From the prod machine no credential is needed at all, Cloudflare is not on that path:
#   PORTFOLIO_BASE_URL=http://localhost:8080 scripts/tao.sh 15.8678

set -euo pipefail

BASE_URL="${PORTFOLIO_BASE_URL:-https://portfolio.tradeengine.app}"
ENV_FILE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/config/cf-access.env"

key="tao-staking"
amount=""
assume_yes=0
snapshot=0

while [ $# -gt 0 ]; do
    case "$1" in
        --key)      key="${2:?--key needs a value}"; shift 2 ;;
        --snapshot) snapshot=1; shift ;;
        -y|--yes)   assume_yes=1; shift ;;
        -h|--help)  sed -n '3,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'; exit 0 ;;
        -*)         echo "Unknown option: $1" >&2; exit 2 ;;
        *)
            [ -z "$amount" ] || { echo "Give at most one amount (got '$amount' and '$1')." >&2; exit 2; }
            amount="$1"; shift ;;
    esac
done

# A comma is the classic slip here: the body stops being valid JSON and the API answers
# invalid_body, which reads like a server problem rather than a typo.
case "${amount}" in
    *,*) echo "Amount must use a decimal POINT, not a comma: '${amount}' -> '${amount//,/.}'" >&2
         exit 2 ;;
esac
if [ -n "$amount" ] && ! printf '%s' "$amount" | grep -Eq '^[0-9]+(\.[0-9]+)?$'; then
    echo "Amount must be a non-negative decimal number, got '${amount}'." >&2
    exit 2
fi

# Localhost means the request never reaches Cloudflare, so the service token is not needed.
auth=()
case "$BASE_URL" in
    http://localhost*|http://127.0.0.1*|http://[::1]*) ;;
    *)
        [ -r "$ENV_FILE" ] || {
            echo "Missing $ENV_FILE — the Cloudflare Access service token lives there." >&2
            echo "Fill CF_ACCESS_CLIENT_ID and CF_ACCESS_CLIENT_SECRET from Zero Trust ->" >&2
            echo "Access controls -> Service credentials -> tradeengine-staking." >&2
            exit 3
        }
        set -a; . "$ENV_FILE"; set +a
        : "${CF_ACCESS_CLIENT_ID:?empty in $ENV_FILE}"
        : "${CF_ACCESS_CLIENT_SECRET:?empty in $ENV_FILE}"
        auth=(-H "CF-Access-Client-Id: ${CF_ACCESS_CLIENT_ID}"
              -H "CF-Access-Client-Secret: ${CF_ACCESS_CLIENT_SECRET}")
        ;;
esac

# Prints the response body, then the status code on its own last line.
call() {
    curl -sS -m 30 --max-redirs 0 -w '\n%{http_code}' "${auth[@]}" "$@"
}

explain_status() {
    case "$1" in
        200|201) return 0 ;;
        30*) echo "  -> Cloudflare Access refused the credential (a 3xx is never the API itself)." >&2
             echo "     Service token expired, or no Service Auth policy on the application" >&2
             echo "     covering /api/portfolio/manual-holdings. See CLAUDE.md." >&2 ;;
        400) echo "  -> Rejected body. A comma instead of a decimal point does this." >&2 ;;
        404) echo "  -> No manual holding declared under key '${key}'." >&2 ;;
        000) echo "  -> No answer at all: ${BASE_URL} unreachable (tunnel down, or app not running)." >&2 ;;
    esac
    return 1
}

show() {
    local body="$1"
    if command -v python3 >/dev/null 2>&1; then
        printf '%s' "$body" | python3 -c "
import json, sys
data = json.load(sys.stdin)
for h in (data if isinstance(data, list) else [data]):
    print('  {:<14}{:>14}   {:<6}{}'.format(h['key'], h['amount'], h['asset'], h['updatedAt']))
"
    else
        printf '  %s\n' "$body"
    fi
}

out=$(call "${BASE_URL}/api/portfolio/manual-holdings") || true
code="${out##*$'\n'}"; body="${out%$'\n'*}"
explain_status "$code" || { echo "GET manual-holdings -> HTTP ${code}" >&2; exit 1; }

echo "Current (${BASE_URL}):"
show "$body"

if [ -z "$amount" ]; then
    exit 0
fi

current=$(printf '%s' "$body" | python3 -c "
import json, sys
print(next((str(h['amount']) for h in json.load(sys.stdin) if h['key'] == '${key}'), ''))
" 2>/dev/null || true)

if [ -z "$current" ]; then
    echo "Key '${key}' is not among the declared manual holdings." >&2
    exit 2
fi

echo
echo "Change ${key}: ${current} -> ${amount}"

# A rise is ordinary for staking (rewards plus top-ups) and says nothing. A FALL is the case worth
# stopping on: unstaking, an order-of-magnitude slip, or a misplaced decimal point.
if python3 -c "import sys; sys.exit(0 if float('${amount}') < float('${current}') else 1)"; then
    echo "WARNING: this LOWERS the amount. Unstaked, or a slip? A fall is the one suspicious case."
    assume_yes=0
fi

if [ "$assume_yes" -eq 0 ]; then
    printf 'Proceed? [y/N] '
    read -r reply
    case "$reply" in [yY]|[yY][eE][sS]) ;; *) echo "Aborted, nothing sent."; exit 0 ;; esac
fi

out=$(call -X PUT -H 'Content-Type: application/json' \
      -d "{\"amount\": ${amount}}" \
      "${BASE_URL}/api/portfolio/manual-holdings/${key}") || true
code="${out##*$'\n'}"; body="${out%$'\n'*}"
explain_status "$code" || { echo "PUT -> HTTP ${code}: ${body}" >&2; exit 1; }

echo "Updated:"
show "$body"

if [ "$snapshot" -eq 1 ]; then
    echo
    echo "Forcing a snapshot (the hourly one would pick it up anyway)..."
    out=$(call -X POST "${BASE_URL}/api/portfolio/snapshots") || true
    code="${out##*$'\n'}"; body="${out%$'\n'*}"
    explain_status "$code" || { echo "POST snapshots -> HTTP ${code}" >&2; exit 1; }
    printf '%s' "$body" | python3 -c "
import json, sys
d = json.load(sys.stdin)
print('  takenAt {}  complete={}  total {:.2f} USD'.format(d['takenAt'], d['complete'], d['totalUsd']))
stale = [q['source'] + '/' + q['asset'] for q in d['positions'] if q['stale']]
print('  stale: ' + (', '.join(stale) if stale else 'none'))
" 2>/dev/null || printf '  %s\n' "$body"
fi
