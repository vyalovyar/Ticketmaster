#!/bin/sh
set -e

if [ -z "${NGINX_RESOLVER}" ]; then
    NGINX_RESOLVER=$(awk '/^nameserver/{print $2; exit}' /etc/resolv.conf)
    case "${NGINX_RESOLVER}" in
        *:*) NGINX_RESOLVER="[${NGINX_RESOLVER}]" ;;
    esac
fi

: "${GATEWAY_HOST:=gateway-service.railway.internal}"
: "${GATEWAY_PORT:=8080}"

export NGINX_RESOLVER GATEWAY_HOST GATEWAY_PORT

exec /docker-entrypoint.sh "$@"
