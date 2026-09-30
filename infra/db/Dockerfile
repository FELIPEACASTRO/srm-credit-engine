# Postgres 17 com o script de init ASSADO na imagem (nao bind-mount): o compose
# roda em qualquer Windows sem depender do File Sharing do Docker Desktop.
FROM postgres:17
COPY db-init/ /docker-entrypoint-initdb.d/
