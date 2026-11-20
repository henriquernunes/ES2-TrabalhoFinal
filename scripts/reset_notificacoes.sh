#!/usr/bin/env bash
# limpa a tabela notificacoes, as filas reengajamento.* e os emails do mailpit (o moodle fica igual)
# variável de ambiente (ex: RETRY_BASE_DELAY_MS) passa por cima do .env, por isso recria o notificacoes
set -euo pipefail
cd "$(dirname "$0")/.."
# lê só o que precisa num subshell, senão o .env sobrescreve as variáveis de quem chamou
read -r DB_USER DB_NAME < <(set -a; . ./.env; echo "$NOTIFICACOES_DB_USER $NOTIFICACOES_DB_NAME")

docker compose up -d --wait mailpit rabbitmq notificacoes-db >/dev/null 2>&1
# para o consumidor antes de limpar
docker compose stop notificacoes >/dev/null 2>&1 || true

docker compose exec -T notificacoes-db psql -q -U "$DB_USER" -d "$DB_NAME" \
  -c "DO \$\$ BEGIN IF to_regclass('notificacoes') IS NOT NULL THEN TRUNCATE notificacoes RESTART IDENTITY; END IF; END \$\$;" >/dev/null
for queue in $(docker compose exec -T rabbitmq rabbitmqctl list_queues name --quiet --no-table-headers | grep '^reengajamento\.' || true); do
  docker compose exec -T rabbitmq rabbitmqctl purge_queue "$queue" --quiet >/dev/null
done
curl -sf -X DELETE http://localhost:8025/api/v1/messages >/dev/null

docker compose up -d --wait --no-deps --force-recreate notificacoes >/dev/null 2>&1
echo "[reset] notificações, filas e Mailpit zerados"
