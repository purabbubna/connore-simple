#!/usr/bin/env bash
set -euo pipefail
cd -- "$(dirname -- "$0")"
if [ ! -f .env ] && [ ! -f config.properties ] && [ -z "${DB_USER+x}" ]; then
  cp .env.example .env
  echo 'Set your MySQL username and password in .env, then run again.'
  exit 1
fi
java_command=java
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  java_command="$JAVA_HOME/bin/java"
fi
"$java_command" Build.java
exec "$java_command" -cp 'out:lib/*' com.connore.Main --open-browser
