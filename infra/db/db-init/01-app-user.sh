#!/bin/bash
# Cria o login da APLICACAO no primeiro boot do volume. Os privilegios vem do papel
# app_rw (criado e concedido na migration V2): sem UPDATE/DELETE/TRUNCATE nas tabelas
# append-only. A senha vem do ambiente - nunca hardcoded em SQL versionado.
set -euo pipefail

# A senha entra como VARIAVEL do psql e e citada com :'pw' — o psql escapa aspas simples
# sozinho, entao uma senha com ' nao quebra (nem injeta) o CREATE ROLE.
psql -v ON_ERROR_STOP=1 -v pw="$APP_DB_PASSWORD" \
	--username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-SQL
	CREATE ROLE credit_engine_app LOGIN PASSWORD :'pw';
SQL
