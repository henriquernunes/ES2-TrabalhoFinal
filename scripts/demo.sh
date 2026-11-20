#!/usr/bin/env bash
# roteiro da apresentação. enter entre os passos, --no-pause roda direto
#   1. ambiente (eureka + gateway) e as 3 chamadas rest do moodle
#   2. ciclo e emails (critérios 1 e 2)
#   3. reprocessar sem reenviar (critério 3)
#   4. smtp fora + retry (critério 4)
set -euo pipefail
cd "$(dirname "$0")/.."
set -a; source .env; set +a

PAUSE=1
[ "${1:-}" = "--no-pause" ] && PAUSE=0
# tudo passa pelo gateway
GATEWAY="http://localhost:${GATEWAY_PORT}"
ACOMP="$GATEWAY/acompanhamento"
NOTIF="$GATEWAY/notificacoes"

bold() { printf '\n\033[1;36m==> %s\033[0m\n' "$*"; }
pause() { [ "$PAUSE" = 1 ] && read -rp $'\n[Enter para continuar] ' _ || true; }
run() { printf '\033[2m$ %s\033[0m\n' "$*"; eval "$@"; }
json() { python3 -m json.tool; }
emails() {
  curl -s http://localhost:8025/api/v1/messages?limit=100 | python3 -c '
import sys, json
d = json.load(sys.stdin)
print("Mailpit: %s e-mail(s)" % d["total"])
for m in sorted(d["messages"], key=lambda m: (m["Subject"], m["To"][0]["Address"])):
    print("  %-22s %s" % (m["To"][0]["Address"], m["Subject"]))'
}
notificacoes() {
  curl -s "$NOTIF/notificacoes${1:+?status=$1}" | python3 -c '
import sys, json
for n in json.load(sys.stdin):
    erro = (n["ultimo_erro"] or "-").splitlines()[0][:90]
    print("  %-8s %-9s tentativas=%s erro=%s" % (n["idempotency_key"], n["status"], n["tentativas"], erro))'
}
# depois de recriar um serviço o gateway demora uns segundos pra achar o ip novo
wait_routes() {
  for _ in $(seq 1 45); do
    [ "$(curl -s -o /dev/null -w '%{http_code}' "$ACOMP/status")" = 200 ] &&
      [ "$(curl -s -o /dev/null -w '%{http_code}' "$NOTIF/notificacoes")" = 200 ] && return 0
    sleep 2
  done
  echo "gateway sem rota para os serviços" >&2; return 1
}
reset_notificacoes() { scripts/reset_notificacoes.sh && wait_routes; }
moodle() { curl -s "http://localhost:${MOODLE_PORT}/webservice/rest/server.php" -d wstoken="$MOODLE_TOKEN" -d moodlewsrestformat=json -d wsfunction="$@"; }

bold "1. Ambiente: Moodle, RabbitMQ, Mailpit, Eureka, API Gateway e os dois microsserviços"
run "docker compose ps --format 'table {{.Service}}\t{{.Status}}'"
echo "Moodle:   http://localhost:${MOODLE_PORT}   (admin: ${MOODLE_ADMIN_USER})"
echo "RabbitMQ: http://localhost:15672          Mailpit: http://localhost:8025"
echo "Eureka:   http://localhost:${EUREKA_PORT}            Gateway: $GATEWAY"
echo "Swagger:  http://localhost:${ACOMPANHAMENTO_PORT}/docs   http://localhost:${NOTIFICACOES_PORT}/docs"
echo "Serviços registrados no Eureka:"
curl -s -H 'Accept: application/json' "http://localhost:${EUREKA_PORT}/eureka/apps" | python3 -c '
import sys, json
for a in json.load(sys.stdin)["applications"]["application"]:
    for i in a["instance"]:
        print("  %-15s %s:%s %s" % (a["name"], i["ipAddr"], i["port"]["$"], i["status"]))'
pause

bold "Seed: prazos relativos a agora (T1/T2 ~12 h, T3 3 dias, T4 vencida, T5 sem prazo)"
run "scripts/seed_moodle.sh --reset-dates | grep -E 'T[1-5]|aluno'"
set -a; source .env; set +a
run "reset_notificacoes"
pause

bold "As 3 chamadas REST ao Moodle (síncronas)"
echo "mod_assign_get_assignments:"
moodle mod_assign_get_assignments -d "courseids[0]=${MOODLE_COURSE_ID}" -d includenotenrolledcourses=1 | python3 -c '
import sys, json, datetime
for a in json.load(sys.stdin)["courses"][0]["assignments"]:
    due = datetime.datetime.fromtimestamp(a["duedate"]).strftime("%d/%m %H:%M") if a["duedate"] else "sem prazo"
    print("  id=%s cmid=%s %-40s prazo=%s" % (a["id"], a["cmid"], a["name"], due))'
echo "core_enrol_get_enrolled_users:"
moodle core_enrol_get_enrolled_users -d "courseid=${MOODLE_COURSE_ID}" | python3 -c '
import sys, json
for u in json.load(sys.stdin):
    print("  id=%s %-15s %-22s %s" % (u["id"], u["fullname"], u["email"], [r["shortname"] for r in u["roles"]]))'
echo "mod_assign_get_submissions (T2):"
moodle mod_assign_get_submissions -d "assignmentids[0]=2" | python3 -c '
import sys, json
for a in json.load(sys.stdin)["assignments"]:
    print("  ", [(s["userid"], s["status"]) for s in a["submissions"]])'
pause

bold "2. Ciclo manual: POST /run (critérios 1 e 2)"
run "curl -s -X POST $ACOMP/run | json"
sleep 3
emails
echo "-> T1: os 5 alunos; T2: 4 alunos (aluno1 já enviou); T3, T4, T5: ninguém."
pause

bold "3. Reprocessar uma solicitação já ENVIADA (critério 3)"
run "curl -s -X POST $NOTIF/notificacoes/${MOODLE_COURSE_ID}:1:4/reprocessar | json"
run "curl -s -X POST $ACOMP/run | python3 -c 'import sys,json; print(\"solicitacoes_publicadas:\", json.load(sys.stdin)[\"solicitacoes_publicadas\"])'"
sleep 3
run "docker compose logs --no-log-prefix --since 10s notificacoes | grep 'duplicada ignorada' | head -3"
emails
echo "-> continua 1 e-mail por aluno/tarefa."
pause

bold "4. Falha de SMTP e nova tentativa (critério 4)"
run "reset_notificacoes"
run "docker compose stop mailpit"
run "curl -s -X POST $ACOMP/run | python3 -c 'import sys,json; print(\"solicitacoes_publicadas:\", json.load(sys.stdin)[\"solicitacoes_publicadas\"])'"
sleep 4
echo "Registros com falha (banco):"
notificacoes PENDENTE
run "docker compose logs --no-log-prefix --since 15s notificacoes | grep -E 'falha ao enviar e-mail|nova tentativa agendada' | head -4"
echo "Filas de retry (TTL + dead-letter de volta para a principal):"
run "docker compose exec -T rabbitmq rabbitmqctl list_queues name messages --quiet | grep reengajamento"
pause

run "docker compose start mailpit"
echo "Aguardando as novas tentativas (backoff ${RETRY_BASE_DELAY_MS} ms, dobrando)..."
for _ in $(seq 1 90); do
  sent=$(curl -s "$NOTIF/notificacoes?status=ENVIADO" | python3 -c 'import sys,json; print(len(json.load(sys.stdin)))')
  [ "$sent" -ge 9 ] && break
  sleep 2
done
notificacoes
emails

bold "Fim. Logs: make logs | Filas: http://localhost:15672 | E-mails: http://localhost:8025"
