#!/usr/bin/env bash
# seed do moodle (roda o php dentro do container), pode rodar quantas vezes quiser
#
#   scripts/seed_moodle.sh                # configura tudo (não mexe nos prazos)
#   scripts/seed_moodle.sh --reset-dates  # recalcula os prazos de T1..T5 a partir de agora
#
# no final grava MOODLE_TOKEN e MOODLE_COURSE_ID no .env (--no-env só imprime)
# e recria o acompanhamento se estiver rodando
set -euo pipefail

cd "$(dirname "$0")/.."

WRITE_ENV=1
PHP_ARGS=()
for arg in "$@"; do
  case "$arg" in
    --no-env) WRITE_ENV=0 ;;
    --reset-dates) PHP_ARGS+=("--reset-dates") ;;
    -h|--help) sed -n '2,8p' "$0"; exit 0 ;;
    *) echo "opção desconhecida: $arg" >&2; exit 2 ;;
  esac
done

echo "[seed] aguardando o Moodle ficar saudável..."
for _ in $(seq 1 90); do
  status=$(docker inspect -f '{{.State.Health.Status}}' "$(docker compose ps -q moodle)" 2>/dev/null || echo "down")
  [ "$status" = "healthy" ] && break
  sleep 5
done
[ "$status" = "healthy" ] || { echo "[seed] Moodle não ficou saudável (status=$status). Rode 'make up' antes." >&2; exit 1; }

docker compose cp scripts/moodle/seed.php moodle:/tmp/seed.php >/dev/null 2>&1
output=$(docker compose exec -T moodle php /tmp/seed.php ${PHP_ARGS[@]+"${PHP_ARGS[@]}"})
echo "$output"

token=$(echo "$output" | sed -n 's/^MOODLE_TOKEN=//p')
course_id=$(echo "$output" | sed -n 's/^MOODLE_COURSE_ID=//p')
[ -n "$token" ] && [ -n "$course_id" ] || { echo "[seed] não foi possível ler o token/curso da saída" >&2; exit 1; }

set_env() { # set_env CHAVE VALOR (awk pra funcionar no mac e no linux)
  local tmp
  tmp=$(mktemp)
  if grep -q "^$1=" .env; then
    awk -v k="$1" -v v="$2" 'BEGIN{FS=OFS="="} $1==k{print k"="v; next} {print}' .env > "$tmp"
  else
    cat .env > "$tmp"; echo "$1=$2" >> "$tmp"
  fi
  cat "$tmp" > .env && rm -f "$tmp"
}

echo
echo "================================================================"
echo " Token do webservice: $token"
echo " Disciplina (MOODLE_COURSE_ID): $course_id"
echo "================================================================"

if [ "$WRITE_ENV" = 1 ] && [ -f .env ]; then
  set_env MOODLE_TOKEN "$token"
  set_env MOODLE_COURSE_ID "$course_id"
  echo "[seed] .env atualizado com MOODLE_TOKEN e MOODLE_COURSE_ID"
  if [ -n "$(docker compose ps -q acompanhamento 2>/dev/null)" ]; then
    docker compose up -d --no-deps --force-recreate acompanhamento >/dev/null 2>&1
    echo "[seed] acompanhamento recriado para carregar o token"
  fi
else
  echo "[seed] cole os valores acima no .env"
fi
