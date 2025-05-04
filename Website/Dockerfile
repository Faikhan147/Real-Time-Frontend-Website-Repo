FROM nginx:alpine
RUN apk add --no-cache curl
RUN rm -rf /usr/share/nginx/html/*
COPY . /usr/share/nginx/html
RUN echo "OK" > /usr/share/nginx/html/healthz
HEALTHCHECK --interval=10s --timeout=3s --retries=3 \
CMD curl --fail http://localhost/healthz || exit 1
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]
