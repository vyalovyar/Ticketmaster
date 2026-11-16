FROM nginx:alpine

COPY frontend/index.html frontend/styles.css frontend/app.js /usr/share/nginx/html/
COPY frontend/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY frontend/resolver-entrypoint.sh /resolver-entrypoint.sh

ENV NGINX_ENVSUBST_FILTER="^(GATEWAY_HOST|GATEWAY_PORT|NGINX_RESOLVER)$"

RUN chmod +x /resolver-entrypoint.sh

EXPOSE 80

ENTRYPOINT ["/resolver-entrypoint.sh"]
CMD ["nginx", "-g", "daemon off;"]
