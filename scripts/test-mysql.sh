#!/usr/bin/env bash
# Disposable test database only. Never point this script at a shared database.
set -euo pipefail
repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root/backend"
mysql_image='mysql:8.4@sha256:a81d043930e51745522bd5c430961cf8286f0a23f0a9a2a6e0108f7ea71b0902'
container="ipola-mysql-test-$(date +%s)-$$"
export MYSQL_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_ROOT_PASSWORD="$(openssl rand -hex 24)"
export MYSQL_DATABASE=ipola_test MYSQL_USER=ipola_test
cleanup() { docker rm -f "$container" >/dev/null 2>&1 || true; }
trap cleanup EXIT
# Publishing on loopback and using tmpfs ensures there is no persistent database to destroy.
docker run -d --name "$container" --tmpfs /var/lib/mysql:rw,size=512m \
  -e MYSQL_PASSWORD -e MYSQL_ROOT_PASSWORD -e MYSQL_DATABASE -e MYSQL_USER \
  -p 127.0.0.1::3306 "$mysql_image" \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci >/dev/null
ready=false
for attempt in $(seq 1 60); do
  if docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -e "SELECT 1"' >/dev/null 2>&1; then
    ready=true; break
  fi
  sleep 1
done
if [ "$ready" != true ]; then
  echo 'Disposable MySQL did not become ready in 60 seconds.' >&2
  exit 1
fi
port="$(docker port "$container" 3306/tcp | sed -n 's/^127\.0\.0\.1://p')"
[[ "$port" =~ ^[0-9]+$ ]]
export TEST_DB_URL="jdbc:mysql://127.0.0.1:$port/ipola_test?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true"
export TEST_DB_USERNAME="$MYSQL_USER" TEST_DB_PASSWORD="$MYSQL_PASSWORD" TEST_DB_EXPECTED_PRODUCT=MySQL
mkdir -p target/mysql-evidence
printf '%s\n' "$mysql_image" > target/mysql-evidence/image.txt
docker exec "$container" sh -c 'MYSQL_PWD="$MYSQL_PASSWORD" mysql --protocol=TCP -h127.0.0.1 -u"$MYSQL_USER" "$MYSQL_DATABASE" -N -e "SELECT VERSION(), @@character_set_database, @@session.time_zone"' > target/mysql-evidence/database-version.txt
for run in fresh repeat; do
  # The second run preserves the same database, validating Flyway startup without clean/reset.
  "${MVN_BIN:-mvn}" -B -ntp verify 2>&1 | tee "target/mysql-evidence/$run.log"
  mkdir -p "target/mysql-evidence/$run-reports"
  cp target/surefire-reports/TEST-*.xml target/surefire-reports/*.txt "target/mysql-evidence/$run-reports/"
done
echo 'MySQL fresh migration and repeat startup passed; evidence: backend/target/mysql-evidence/'
