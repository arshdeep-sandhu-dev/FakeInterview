#!/usr/bin/env bash
# Prints a random bug ticket. Pass a ticket id to pick a specific one: ./pick-ticket.sh SUP-104
set -euo pipefail
cd "$(dirname "$0")/tickets"

if [ "$#" -gt 0 ]; then
  pick="$1.md"
  [ -f "$pick" ] || { echo "No ticket named $1" >&2; exit 1; }
else
  files=(SUP-*.md)
  pick=${files[RANDOM % ${#files[@]}]}
fi

echo
echo "==================== YOUR TICKET: ${pick%.md} ===================="
echo
cat "$pick"
echo
echo "Start your timer. Open your agent in ./shipfast and talk out loud."
