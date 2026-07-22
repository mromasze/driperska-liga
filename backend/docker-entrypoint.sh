#!/bin/sh
set -e

# The media directory is a named Docker volume mounted over /app/data/media. A fresh
# or pre-existing volume is root-owned, which the unprivileged 'app' user cannot write
# to — that is why avatar uploads failed. Fix ownership here at startup (we start as
# root) and then drop to 'app' to run the JVM.
MEDIA_DIR="${MEDIA_DIR:-/app/data/media}"
mkdir -p "$MEDIA_DIR/avatars"
chown -R app "$MEDIA_DIR" 2>/dev/null || true

exec gosu app sh -c "java $JAVA_OPTS -jar /app/app.jar"
