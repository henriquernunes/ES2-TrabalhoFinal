# Registro dos testes

Execução de referência: **20/11/2026**, macOS (Apple Silicon), Docker 29 / Compose 5, Java 21. Fluxo completo do zero:

```bash
make clean && make up && make seed && make e2e
```

Resultado: **4 de 4 critérios aprovados** (`Tests run: 4, Failures: 0, Errors: 0`), em 165 s a partir de volumes vazios. Log completo, com o token mascarado: [`tests/e2e/evidencias/execucao-completa.log`](../tests/e2e/evidencias/execucao-completa.log).

O E2E ([`tests/e2e/src/test/java/br/pucrs/es2/e2e/CriteriosAceiteE2ETest.java`](../tests/e2e/src/test/java/br/pucrs/es2/e2e/CriteriosAceiteE2ETest.java)) roda contra o ambiente real do compose. Todas as chamadas aos serviços passam pelo **API Gateway** (`localhost:9000`), com as rotas `lb://` resolvidas no **Eureka**. Os e-mails são conferidos pela API do Mailpit (`GET /api/v1/messages`). Antes dos critérios 1 e 4, `scripts/reset_notificacoes.sh` zera a tabela `notificacoes`, as filas e o Mailpit. O cenário de falha usa `RETRY_BASE_DELAY_MS=3000` e `SMTP_TIMEOUT_MS=2000` para não demorar minutos.

Massa de dados (seed, prazos relativos ao momento do `--reset-dates`): disciplina *Engenharia de Software II*, 5 alunos (aluno1..5), tarefas T1 e T2 vencendo em ~12 h (aluno1 já enviou a T2), T3 em 3 dias, T4 vencida há 2 dias e T5 sem prazo.

## Critério 1: lembrete para tarefa pendente dentro da janela de 24 h

| | |
|---|---|
| **Cenário** | T1 vence em ~12 h e nenhum aluno enviou. |
| **Passos** | `reset_notificacoes.sh` → `POST /acompanhamento/run` (gateway) → aguardar os e-mails no Mailpit → conferir destinatários e conteúdo. |
| **Esperado** | O ciclo analisa 5 tarefas, 2 na janela, 5 alunos, e publica 9 solicitações sem erros. Cada um dos 5 alunos recebe exatamente 1 lembrete da T1, com o nome do aluno, o nome da tarefa, o prazo em horário de Brasília e o link `…/mod/assign/view.php?id=<cmid>`. |
| **Obtido** | `tarefas_analisadas=5`, `tarefas_na_janela=2`, `alunos_analisados=5`, `solicitacoes_publicadas=9`, `erros=[]`. 1 e-mail da T1 para cada aluno. Exemplo: "vence em sábado, 21 de novembro de 2026 às 05:55 (horário de Brasília)" e o link `http://localhost:8080/mod/assign/view.php?id=2`. **Aprovado.** |
| **Comando** | `make e2e` (teste `criterio1`) |
| **Evidências** | [`criterio-1-2-ciclo.json`](../tests/e2e/evidencias/criterio-1-2-ciclo.json), [`criterio-1-exemplo-email.txt`](../tests/e2e/evidencias/criterio-1-exemplo-email.txt) |

## Critério 2: nenhum lembrete para tarefa já enviada ou fora da janela

| | |
|---|---|
| **Cenário** | T2 vence em ~12 h, mas o aluno1 já enviou. T3 vence em 3 dias, T4 venceu e T5 não tem prazo. |
| **Passos** | Mesmo ciclo do critério 1; espera 3 s extras por algum e-mail indevido e confere o Mailpit e o registro de notificações. |
| **Esperado** | Nenhum e-mail da T2 para o aluno1, 1 para cada um dos outros 4. Nenhum e-mail de T3, T4 ou T5. Total de 9 e-mails. |
| **Obtido** | 9 e-mails: 5 da T1 e 4 da T2 (aluno2..5). Nenhum para aluno1/T2, nenhum de T3/T4/T5. 9 registros `ENVIADO` com 1 tentativa cada. **Aprovado.** |
| **Comando** | `make e2e` (teste `criterio2`) |
| **Evidências** | [`criterio-1-2-mailpit.json`](../tests/e2e/evidencias/criterio-1-2-mailpit.json), [`criterio-1-2-notificacoes.json`](../tests/e2e/evidencias/criterio-1-2-notificacoes.json) |

## Critério 3: nenhum novo envio ao reprocessar uma solicitação já concluída

| | |
|---|---|
| **Cenário** | A solicitação `2:1:4` (aluno1, T1) já está `ENVIADO`. |
| **Passos** | `POST /notificacoes/notificacoes/2:1:4/reprocessar` (republica o payload original na fila principal) → novo `POST /acompanhamento/run` (republica as 9 solicitações) → aguardar o consumo e conferir log, registro e Mailpit. |
| **Esperado** | `202` com `status_atual=ENVIADO`. As 10 mensagens republicadas (1 + 9) são ignoradas como duplicadas, 2 delas com a chave `2:1:4`. O total de e-mails continua 9, e o registro `2:1:4` continua `ENVIADO` com 1 tentativa. |
| **Obtido** | `202`, `status_atual=ENVIADO`, mensagem "republicada; já ENVIADO, o consumidor vai ignorar (idempotência)". O novo ciclo publicou 9. O log tem 10 linhas `duplicada ignorada`, 2 com `idempotency_key=2:1:4`. E-mails antes: 9, depois: 9. Registro `2:1:4`: `ENVIADO`, `tentativas=1`. **Aprovado.** |
| **Comando** | `make e2e` (teste `criterio3`) |
| **Evidências** | [`criterio-3-reprocessar.json`](../tests/e2e/evidencias/criterio-3-reprocessar.json), [`criterio-3-logs.log`](../tests/e2e/evidencias/criterio-3-logs.log) |

## Critério 4: registro de falha e nova tentativa

| | |
|---|---|
| **Cenário** | Servidor SMTP (Mailpit) fora do ar durante o ciclo. |
| **Passos** | `reset_notificacoes.sh` → `docker compose stop mailpit` → `POST /acompanhamento/run` → conferir a falha no banco (`GET /notificacoes?status=PENDENTE`) e no log → `docker compose start mailpit` → aguardar as novas tentativas e conferir os e-mails. |
| **Esperado** | Registros `PENDENTE` com `tentativas ≥ 1` e `ultimo_erro` preenchido. Log `nova tentativa agendada` com backoff crescente. Com o Mailpit de volta, os 9 e-mails chegam, os 9 registros ficam `ENVIADO` sem erro (alguns com `tentativas ≥ 2`) e nenhum fica `FALHOU`. |
| **Obtido** | 9 registros `PENDENTE`, `tentativas=1`, `ultimo_erro` = `MailSendException: Mail server connection failed … Couldn't connect to host, port: mailpit, 1025 … UnknownHostException: mailpit`. Log com 27 `falha ao enviar e-mail` e 27 `nova tentativa agendada`, atrasos de 3 s, 6 s e 12 s (filas `retry.3000ms`, `retry.6000ms`, `retry.12000ms`). Depois que o Mailpit voltou: 9 `e-mail enviado`, 9 registros `ENVIADO` com `tentativas=4` e `ultimo_erro=null`, 9 e-mails no Mailpit, 0 `FALHOU`. **Aprovado.** |
| **Comando** | `make e2e` (teste `criterio4`) |
| **Evidências** | [`criterio-4-falha-notificacoes.json`](../tests/e2e/evidencias/criterio-4-falha-notificacoes.json), [`criterio-4-depois-notificacoes.json`](../tests/e2e/evidencias/criterio-4-depois-notificacoes.json), [`criterio-4-logs.log`](../tests/e2e/evidencias/criterio-4-logs.log) |

O esgotamento das tentativas (DLQ + `FALHOU`) e a mensagem inválida (direto para a DLQ) são cobertos pelos testes unitários do `SolicitacoesListener`. Também foram conferidos à mão durante o desenvolvimento: uma mensagem `lixo` publicada pelo painel do RabbitMQ foi para `reengajamento.dlq`.

## Testes unitários e de integração (`make test`)

JUnit 6 + Mockito + AssertJ, sem depender do compose (H2 em modo PostgreSQL para o JPA; Eureka e listener do RabbitMQ desligados nos testes). **68 testes, 0 falhas.** Logs: `tests/e2e/evidencias/unitarios-*.log`.

| Projeto | Classe | Testes | O que cobre |
|---|---|---|---|
| acompanhamento | `ReminderRulesTest` | 13 | Janela de 24 h com bordas (sem prazo, vencida, exatamente agora, +1 s, fim exato da janela, +1 s depois, 3 dias), janela configurável, seleção T1/T2, chave e URL, exclusão de quem já enviou, contrato da mensagem |
| | `MoodleClientTest` | 6 | Corpo form-urlencoded com `wstoken`/`wsfunction`/`moodlewsrestformat`, cabeçalho `Host` público, `exception` do Moodle → erro com `errorcode`, timeout, resposta HTML, sem token não chama a rede (`MockRestServiceServer`) |
| | `MoodleParamsTest` | 1 | Arrays/mapas no formato `courseids[0]`, `options[0][name]`, booleanos 1/0 |
| | `MoodleServiceTest` | 5 | Parâmetros das 3 funções, disciplina ausente, filtro por papel `student` e e-mail, só `submitted` conta |
| | `ReengajamentoServiceTest` | 5 | 1 publicação por par pendente, não consulta alunos sem tarefa na janela, falha do Moodle registrada sem lançar, falha de publicação não interrompe o ciclo, um ciclo por vez |
| | `ReengajamentoSchedulerTest` | 3 | Intervalo vem de `poll-interval-seconds`, disparo agendado, pula se o ciclo anterior ainda roda |
| | `ReengajamentoControllerTest` | 3 | `POST /run` (JSON em snake_case), `409` com ciclo em andamento, `GET /status` (`@WebMvcTest`) |
| | `AcompanhamentoApplicationTests` | 1 | Contexto sobe; mensagem serializada no contrato snake_case |
| notificacoes | `SolicitacoesListenerTest` | 7 | Sucesso → ack; 1ª falha → `retry.5000ms` com `x-retry-count=1`; 3ª falha → `retry.20000ms`; esgotado → DLQ + `FALHOU`; JSON malformado e campos inválidos → DLQ; falha ao encaminhar → `nack(requeue)` sem ack |
| | `NotificacoesServiceTest` | 7 | Chave nova envia e marca `ENVIADO`; já `ENVIADO` não reenvia; falha SMTP registra erro e tentativa; `FALHOU` é reaberto; `marcarFalhou`; reprocessar republica mesmo se `ENVIADO`; chave inexistente → 404 |
| | `RetryPolicyTest` | 4 | Backoff 5/10/20/40/80 s, nome das filas, DLQ após `MAX_RETRIES` |
| | `LembreteTemplateTest` | 3 | Prazo "sábado, 21 de novembro de 2026 às 12:30 (horário de Brasília)", conteúdo e link, HTML escapado |
| | `MailServiceTest` | 1 | Remetente, destinatário, assunto e cabeçalho `X-Idempotency-Key` |
| | `NotificacoesControllerTest` | 4 | Filtro por status, status inválido → 400, reprocessar → 202, chave inexistente → 404 |
| | `NotificacaoRepositoryTest` | 2 | Migration Flyway + mapeamento JPA (`@DataJpaTest`); `idempotency_key` UNIQUE; busca por chave e status |
| | `NotificacoesApplicationTests` | 1 | Contexto sobe; topologia: principal, DLQ e 5 filas de retry com TTL e dead-letter |
| discovery-server | `DiscoveryServerApplicationTests` | 1 | Contexto do Eureka Server sobe |
| api-gateway | `ApiGatewayApplicationTests` | 1 | Contexto do gateway sobe |

## Demo (`make demo`)

`scripts/demo.sh --no-pause` também foi executado de ponta a ponta. O roteiro mostra os serviços registrados no Eureka, as 3 chamadas REST ao Moodle, os 9 e-mails, o reprocessamento ignorado como duplicado e o retry com o Mailpit parado, até os 9 registros ficarem `ENVIADO`.
