#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
exec java -Dfile.encoding=UTF-8 -jar target/campus-work-order-rbac.jar "$@"
