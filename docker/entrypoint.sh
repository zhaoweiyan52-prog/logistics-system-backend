#!/bin/sh
set -eu
umask 077

ca_file="${DB_CA_CERT_PATH:-/etc/secrets/ca.pem}"
if [ ! -s "$ca_file" ]; then
    echo 'Missing MySQL CA certificate. Add the Aiven CA as Render secret file ca.pem.' >&2
    exit 1
fi
if [ -z "${DB_URL:-}" ] || [ -z "${DB_USERNAME:-}" ] || [ -z "${DB_PASSWORD:-}" ]; then
    echo 'DB_URL, DB_USERNAME and DB_PASSWORD must be configured in the hosting dashboard.' >&2
    exit 1
fi

trust_dir="$(mktemp -d /tmp/logistics-trust.XXXXXX)"
trust_store="$trust_dir/mysql-ca.p12"
# This store contains only the public CA certificate, not a password or private key.
keytool -importcert -noprompt -alias aiven-mysql-ca -file "$ca_file" \
    -keystore "$trust_store" -storetype PKCS12 -storepass changeit >/dev/null 2>&1 || {
        echo 'Could not import the MySQL CA certificate. Check the ca.pem secret file.' >&2
        exit 1
    }

exec java \
    -Djavax.net.ssl.trustStore="$trust_store" \
    -Djavax.net.ssl.trustStoreType=PKCS12 \
    -Djavax.net.ssl.trustStorePassword=changeit \
    -jar /app/app.jar "$@"
