#!/usr/bin/env bash
# Run on your own Ubuntu 24.04/Debian EC2 instance as root, after DNS points here.
set -euo pipefail
if [[ "$EUID" != 0 || $# != 2 ]]; then
  echo 'Usage: sudo bash deploy/bootstrap.sh game.example.com email@example.com' >&2
  exit 1
fi
DOMAIN="$1"
EMAIL="$2"
if [[ ! "$DOMAIN" =~ ^[a-zA-Z0-9]([a-zA-Z0-9.-]*[a-zA-Z0-9])?$ || "$DOMAIN" != *.* || "$DOMAIN" == *..* ]]; then
  echo 'Invalid domain' >&2; exit 1
fi
if [[ ! "$EMAIL" =~ ^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$ ]]; then
  echo 'Invalid email' >&2; exit 1
fi
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
APP=/opt/army2-online
if [[ -f /etc/nginx/sites-available/army2-online ]] && ! grep -Fq "server_name $DOMAIN;" /etc/nginx/sites-available/army2-online; then
  echo 'Existing army2 nginx configuration has another domain; inspect before changing it.' >&2
  exit 1
fi
export DEBIAN_FRONTEND=noninteractive
apt-get update
apt-get install -y python3 nginx certbot python3-certbot-nginx
if ! id army2-online >/dev/null 2>&1; then
  useradd --system --no-create-home --shell /usr/sbin/nologin army2-online
fi
install -d -m 0755 "$APP/server" /var/www/army2-client
# Stop only this application's service during an upgrade. Database remains in /var/lib.
if systemctl is-active --quiet army2-online; then systemctl stop army2-online; fi
install -m 0644 "$SCRIPT_DIR"/../server/*.py "$APP/server/"
install -m 0644 "$SCRIPT_DIR/army2-online.service" /etc/systemd/system/army2-online.service
install -m 0755 "$SCRIPT_DIR/backup.sh" "$APP/backup.sh"
install -m 0644 "$SCRIPT_DIR/army2-backup.service" /etc/systemd/system/army2-backup.service
install -m 0644 "$SCRIPT_DIR/army2-backup.timer" /etc/systemd/system/army2-backup.timer
cat >/etc/nginx/conf.d/army2-rate.conf <<'EOF'
limit_req_zone $binary_remote_addr zone=army2:10m rate=8r/s;
EOF
install -d -m 0755 /etc/nginx/snippets
cat >/etc/nginx/snippets/army2-profile.conf <<'EOF'
location = /profile/load {
    limit_req zone=army2 burst=20 nodelay;
    limit_except POST { deny all; }
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 15s;
}
location = /profile/save {
    client_max_body_size 3m;
    limit_req zone=army2 burst=20 nodelay;
    limit_except POST { deny all; }
    proxy_pass http://127.0.0.1:8080;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_connect_timeout 5s;
    proxy_read_timeout 15s;
}
EOF
# Preserve existing certificate configuration on repeat deployments.
if [[ ! -f /etc/nginx/sites-available/army2-online ]]; then
cat >/etc/nginx/sites-available/army2-online <<EOF
server {
    listen 80;
    server_name $DOMAIN;
    client_max_body_size 4k;
    include /etc/nginx/snippets/army2-profile.conf;
    location = /health {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
    location = /jme {
        limit_req zone=army2 burst=20 nodelay;
        limit_except POST { deny all; }
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_connect_timeout 5s;
        proxy_read_timeout 15s;
    }
    location /client/ {
        alias /var/www/army2-client/;
        autoindex off;
        types { application/java-archive jar; text/vnd.sun.j2me.app-descriptor jad; }
    }
    location / { return 404; }
}
EOF
else
  if ! grep -Fq "server_name $DOMAIN;" /etc/nginx/sites-available/army2-online; then
    echo 'Existing army2 nginx configuration has another domain; inspect before changing it.' >&2
    exit 1
  fi
fi
# Add the two routes to an existing managed site without replacing certbot's
# HTTPS configuration. Fail closed if its layout no longer matches our site.
python3 - /etc/nginx/sites-available/army2-online <<'PY'
from pathlib import Path
import sys
site = Path(sys.argv[1])
text = site.read_text()
include = 'include /etc/nginx/snippets/army2-profile.conf;'
if include not in text:
    anchor = 'location = /jme {'
    if text.count(anchor) != 1:
        raise SystemExit('Cannot safely update existing nginx site; inspect it manually.')
    site.write_text(text.replace(anchor, include + '\n    ' + anchor, 1))
PY
ln -sfn /etc/nginx/sites-available/army2-online /etc/nginx/sites-enabled/army2-online
nginx -t
systemctl daemon-reload
systemctl enable --now army2-online army2-backup.timer
systemctl enable --now nginx
systemctl reload nginx
certbot --nginx --non-interactive --agree-tos --email "$EMAIL" -d "$DOMAIN" --redirect
nginx -t
systemctl reload nginx
python3 -c 'import urllib.request; assert b"ok" in urllib.request.urlopen("http://127.0.0.1:8080/health",timeout=10).read()'
if [[ -f "$SCRIPT_DIR/../dist/ARMY2_Online_IOS.jar" ]]; then
  install -m 0644 "$SCRIPT_DIR"/../dist/ARMY2_Online_IOS.{jar,jad} /var/www/army2-client/
fi
echo "Server installed. Verify HTTPS at https://$DOMAIN/health before importing the JAR into Manic EMU."
