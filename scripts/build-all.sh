#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")/.."

echo "Building Relata frontend..."
cd frontend
npm install
npm run build

echo "Building Relata backend..."
cd ../backend
mvn package -DskipTests

echo "Done: backend/target/relata-backend-0.1.0-SNAPSHOT.jar"
