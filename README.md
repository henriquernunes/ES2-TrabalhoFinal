# Reengajamento de estudantes integrado ao Moodle

Trabalho final de Engenharia de Software II (PUCRS, 2026/2).

- **Aluno:** Henrique Nunes
- **Professor:** Julio Machado

O sistema envia **um lembrete por e-mail a cada aluno que ainda não enviou uma tarefa do Moodle que vence nas próximas 24 h**. O e-mail traz o nome da tarefa, o prazo e o link de acesso.

```
                         ┌────────────── Eureka (discovery-server) ──────────────┐
                         │ registro                                     registro │
Moodle ──REST (síncrono)──► acompanhamento ──publica──► RabbitMQ ──consome──► notificacoes ──SMTP──► Mailpit
                                 ▲                                                  ▲
                                 └──────────── API Gateway (lb://, via Eureka) ─────┘
```

Microsserviços Spring Boot 4.2 (Java 21) com Spring Cloud 2026.0:

- **acompanhamento** (produtor): a cada `POLL_INTERVAL_SECONDS` consulta o Moodle (`mod_assign_get_assignments`, `core_enrol_get_enrolled_users`, `mod_assign_get_submissions`) e publica uma mensagem por par (aluno, tarefa) pendente.
- **notificacoes** (consumidor): envia o e-mail com idempotência (Postgres próprio, `idempotency_key` UNIQUE), faz novas tentativas com backoff (filas com TTL + dead-letter) e usa uma DLQ.
- **discovery-server** (Eureka) e **api-gateway** (Spring Cloud Gateway): os dois serviços se registram no Eureka, e o gateway roteia `/acompanhamento/**` e `/notificacoes/**` com balanceamento (`lb://`).

Arquitetura, formato da mensagem, filas e decisões: [`docs/ARQUITETURA.md`](docs/ARQUITETURA.md). Registro dos testes: [`docs/TESTES.md`](docs/TESTES.md).

## Pré-requisitos

- Docker com Docker Compose v2 (testado com Docker 29 e Compose 5) e ~5 GB de RAM livres.
- `make`, `bash`, `curl` e `python3` (usados pelos scripts e pela demo).
- Java 21 (JDK), só para rodar os testes fora dos contêineres (`make test`, `make e2e`). Maven não é necessário: cada projeto tem o `./mvnw`. No macOS o `Makefile` acha o JDK 21 pelo `java_home`; em outros sistemas, exporte `JAVA_HOME`.
- Portas livres: 8080 (Moodle), 9000 (gateway), 8761 (Eureka), 8081, 8082, 5672, 15672, 1025, 8025.

## Instalação e uso

```bash
cp .env.example .env     # o make também cria se não existir
make up                  # build + sobe tudo e espera os healthchecks (1ª instalação do Moodle: ~2 min)
make seed                # configura o Moodle e grava MOODLE_TOKEN/MOODLE_COURSE_ID no .env
curl -X POST localhost:9000/acompanhamento/run   # dispara um ciclo agora (via API Gateway)
```

Depois abra o Mailpit em <http://localhost:8025>: devem aparecer **9 e-mails** (T1 para os 5 alunos; T2 para 4, porque o aluno1 já enviou).

### O seed (`make seed`)

[`scripts/seed_moodle.sh`](scripts/seed_moodle.sh) copia [`scripts/moodle/seed.php`](scripts/moodle/seed.php) para o contêiner e o executa com PHP CLI usando as APIs internas do Moodle. Pode ser rodado quantas vezes quiser (é idempotente):

1. habilita webservices e o protocolo REST;
2. cria o papel e o usuário de serviço `ws_reengajamento` com as capabilities necessárias;
3. cria o serviço externo `reengajamento` com as 3 funções e um token permanente;
4. cria a disciplina **Engenharia de Software II** (`ES2-REENG`) e 5 alunos (`aluno1..5@example.com`, senha `Aluno#2026`);
5. cria as tarefas dos critérios de aceite:

| Tarefa | Prazo | Esperado |
|---|---|---|
| T1 | agora + 12 h | todos recebem |
| T2 | agora + 12 h, aluno1 já enviou | todos menos aluno1 |
| T3 | agora + 3 dias | ninguém (fora da janela) |
| T4 | agora − 2 dias | ninguém (vencida) |
| T5 | sem prazo | ninguém |

Os prazos são relativos ao momento do seed. Antes de uma demo, reposicione com `make reset-dates` (`scripts/seed_moodle.sh --reset-dates`).

### Token do webservice

O seed imprime o token ao final e já o grava no `.env` (`MOODLE_TOKEN` e `MOODLE_COURSE_ID`). Se o `acompanhamento` estiver rodando, o seed recria o contêiner para carregar o token. Para só imprimir: `scripts/seed_moodle.sh --no-env`. Depois cole o valor no `.env` e rode `docker compose up -d acompanhamento`.

Sem token, o serviço sobe normalmente, mas cada ciclo registra o erro `MOODLE_TOKEN não configurado` em `/status`.

## Onde ver as coisas

| O quê | Onde |
|---|---|
| E-mails enviados | Mailpit: <http://localhost:8025> (API: `GET /api/v1/messages`) |
| Filas (principal, retry, DLQ) | RabbitMQ: <http://localhost:15672>, usuário/senha `RABBITMQ_USER`/`RABBITMQ_PASSWORD` do `.env` (padrão `reengajamento`) |
| Serviços registrados | Eureka: <http://localhost:8761> |
| Moodle | <http://localhost:8080>, admin `MOODLE_ADMIN_USER`/`MOODLE_ADMIN_PASSWORD` (padrão `admin` / `Admin#2026`) |
| API acompanhamento | Gateway: `localhost:9000/acompanhamento/...` (`POST /run`, `GET /status`, `GET /health`). Swagger direto no serviço: <http://localhost:8081/docs> |
| API notificacoes | Gateway: `localhost:9000/notificacoes/...` (`GET /notificacoes?status=ENVIADO\|PENDENTE\|FALHOU`, `POST /notificacoes/{key}/reprocessar`, `GET /health`). Swagger: <http://localhost:8082/docs> |
| Rotas do gateway | `GET localhost:9000/actuator/gateway/routes` |
| Logs (JSON, com `idempotency_key`) | `make logs` |

## Configuração (`.env`)

| Variável | Padrão | Descrição |
|---|---|---|
| `POLL_INTERVAL_SECONDS` | `3600` | Intervalo do ciclo. Para a demo use 30–60. |
| `REMINDER_WINDOW_HOURS` | `24` | Janela do lembrete. |
| `MOODLE_COURSE_ID` | `2` | Disciplina monitorada (o seed grava). |
| `MOODLE_URL` / `MOODLE_PUBLIC_URL` | `http://moodle:8080` / `http://localhost:8080` | URL interna (REST) e pública (links dos e-mails e `wwwroot`). |
| `MOODLE_TIMEOUT_MS` | `10000` | Timeout das chamadas REST. |
| `MAX_RETRIES` | `5` | Novas tentativas antes da DLQ. |
| `RETRY_BASE_DELAY_MS` | `5000` | Atraso da 1ª nova tentativa; dobra a cada uma. |
| `SMTP_HOST` / `SMTP_PORT` / `SMTP_FROM` | `mailpit` / `1025` / … | Servidor de e-mail. |
| `SMTP_TIMEOUT_MS` | `5000` | Timeout de conexão/leitura SMTP. |
| `GATEWAY_PORT` / `EUREKA_PORT` | `9000` / `8761` | Portas do gateway e do Eureka no host. |
| `ACOMPANHAMENTO_PORT` / `NOTIFICACOES_PORT` | `8081` / `8082` | Portas diretas dos serviços (Swagger, depuração). |
| `NOTIFICACOES_DB_*` | `notificacoes` | Banco Postgres do serviço notificacoes. |

**Mudar o intervalo:** edite `POLL_INTERVAL_SECONDS` no `.env` e rode `docker compose up -d acompanhamento`. O valor atual aparece em `GET /status` (`intervalo_segundos`).

## Testes

```bash
make test   # JUnit 6 + Mockito + AssertJ nos 4 projetos: regras de janela/filtro, cliente Moodle,
            # idempotência, retry/DLQ, controllers (@WebMvcTest), repositório (@DataJpaTest + Flyway no H2)
make e2e    # E2E (tests/e2e, JUnit): sobe o compose, roda o seed, POST /run pelo gateway e confere os e-mails pela API do Mailpit
```

O E2E cobre os 4 critérios de aceite e grava as evidências em [`tests/e2e/evidencias/`](tests/e2e/evidencias). **Atenção:** ele zera o histórico de notificações, as filas e o Mailpit (`scripts/reset_notificacoes.sh`). O Moodle não é afetado.

Fluxo completo do zero:

```bash
make clean && make up && make seed && make e2e
```

`make clean` equivale a `docker compose down -v`. O `make down -v` literal não funciona porque o `make` interpreta `-v` como a própria opção `--version`.

## Demo

```bash
make demo   # scripts/demo.sh: roteiro passo a passo (Enter entre os passos; --no-pause para rodar direto)
```

O roteiro mostra: os serviços no Eureka e as 3 chamadas REST; o ciclo e os e-mails (critérios 1 e 2); o reprocessamento sem reenvio (3); e o Mailpit parado, com a falha no banco/log, o retry e a entrega (4). Todas as chamadas aos serviços passam pelo gateway.

## Alvos do Makefile

`make up`, `down`, `clean`, `seed`, `reset-dates`, `reset`, `test`, `e2e`, `demo`, `logs`, `ps` (`make help` lista todos).

## Estrutura

```
.
├── docker-compose.yml        # Moodle + Postgres, RabbitMQ, Mailpit, Eureka, gateway, os 2 serviços + Postgres (volumes nomeados)
├── .env.example
├── Makefile
├── discovery-server/         # Eureka Server
├── api-gateway/              # Spring Cloud Gateway (rotas lb:// via Eureka)
├── acompanhamento/           # config/, moodle/ (+dto), messaging/, reengajamento/ (+dto) + testes
├── notificacoes/             # config/, messaging/, mail/, notificacao/ (+dto), db/migration (Flyway) + testes
├── scripts/                  # seed_moodle.sh + moodle/seed.php, reset_notificacoes.sh, demo.sh
├── tests/e2e/                # critérios de aceite (Maven/JUnit) + evidencias/
└── docs/                     # ARQUITETURA.md, TESTES.md
```

## Stack

Os serviços foram feitos em **Spring**, framework utilizado durante o semestre na disciplina.

Java 21 · Spring Boot 4.2 (Spring Framework 7.1, Jackson 3) · Spring Cloud 2026.0 (Netflix Eureka, Gateway Server WebFlux) · Spring Web MVC (`RestClient`) · Spring AMQP (ack manual, publisher confirms) · Spring Data JPA + Flyway + PostgreSQL 17 · Spring Mail (Jakarta Mail) · Bean Validation · springdoc-openapi 3 · Actuator · logs JSON estruturados (`logstash`) · JUnit 6 + Mockito + AssertJ · Maven Wrapper.

## Troubleshooting

| Sintoma | Causa / solução |
|---|---|
| `make up` demora ou o Moodle fica `starting` | Na 1ª vez o Moodle instala o banco (~2 min). Acompanhe com `docker compose logs -f moodle`. |
| Moodle reiniciando com `version 17 is required` | Volume de um Postgres antigo. Rode `make clean` e `make up`. |
| Gateway responde `503 Service Unavailable` | O serviço ainda não apareceu no Eureka (ou foi recriado e o IP mudou). Aguarde ~10 s. Confira em <http://localhost:8761>. |
| `/status` com `MOODLE_TOKEN não configurado` ou `invalidtoken` | Rode `make seed` (grava o token e recria o acompanhamento). Depois de `make clean`, o token muda. |
| `/status` com `nopermissions` | Papel do usuário de serviço desatualizado. Rode `make seed` de novo. |
| `/run` com `tarefas_na_janela: 0` | Os prazos do seed envelheceram. Rode `make reset-dates`. |
| Nenhum e-mail novo depois de `/run` | Esperado se a chave já está `ENVIADO` (idempotência). Para recomeçar, rode `make reset`. |
| Mensagens paradas em `reengajamento.solicitacoes.retry.*` | Falha de SMTP. Verifique o `mailpit` (`docker compose ps`) e o `GET /notificacoes?status=PENDENTE`. |
| Mensagens em `reengajamento.dlq` | Tentativas esgotadas (`FALHOU`) ou mensagem inválida. Para reenviar: `POST /notificacoes/{key}/reprocessar`. |
| Filas `retry.<n>ms` antigas vazias no painel | Criadas por outra configuração de `RETRY_BASE_DELAY_MS` (ex.: o E2E usa 3 s). São inofensivas. |
| `make test` com `release version 21 not supported` | O Maven pegou um JDK antigo. Exporte `JAVA_HOME` de um JDK 21. |
| Porta em uso | Ajuste `MOODLE_PORT`, `GATEWAY_PORT`, `EUREKA_PORT`, `ACOMPANHAMENTO_PORT` ou `NOTIFICACOES_PORT` no `.env`. Se mudar `MOODLE_PORT`, ajuste também `MOODLE_PUBLIC_URL`. |
