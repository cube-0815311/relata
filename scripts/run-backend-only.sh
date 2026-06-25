#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/../backend"
echo "Starting backend API only on http://localhost:8080."
echo "For the UI, run ../frontend with npm run dev and open http://localhost:5173,"
echo "or use scripts/run-full.sh from the repository root."
mvn spring-boot:run -Pbackend-only
