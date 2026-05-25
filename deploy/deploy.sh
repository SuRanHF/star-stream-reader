#!/usr/bin/env bash
set -e

DEPLOY_DIR="/opt/reader-game"
IP=$(curl -s ifconfig.me 2>/dev/null || hostname -I | awk '{print $1}')

echo "=== Star Stream Reader Deploy ==="

mkdir -p $DEPLOY_DIR/{logs,ssl,frontend-player,frontend-admin}

cd springboot-backend
mvn clean package -DskipTests -q
cp target/reader-game-0.1.0.jar $DEPLOY_DIR/reader-game.jar
cd ..

cd frontend-player
npm install --silent
VITE_API_BASE_URL=/api npx vite build --outDir $DEPLOY_DIR/frontend-player
cd ..

cd frontend-admin
npm install --silent
VITE_API_BASE_URL=/api npx vite build --outDir $DEPLOY_DIR/frontend-admin
cd ..

cp deploy/reader-game.service /etc/systemd/system/
cp deploy/nginx.conf /etc/nginx/conf.d/reader-game.conf

systemctl daemon-reload
systemctl enable reader-game
systemctl restart reader-game

nginx -t && nginx -s reload 2>/dev/null || nginx

echo "Done! http://$IP"
echo "Don't forget: vim /etc/systemd/system/reader-game.service  # set passwords"
