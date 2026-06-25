#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

echo "Building Relata frontend..."
cd frontend
npm install
npm run build

echo "Starting Relata backend with frontend assets on http://localhost:8080..."
cd ../backend
mvn spring-boot:run
