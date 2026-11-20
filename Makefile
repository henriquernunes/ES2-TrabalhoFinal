.DEFAULT_GOAL := help
.PHONY: help up down clean seed reset-dates reset test e2e demo logs ps
SHELL := bash
.SHELLFLAGS := -eo pipefail -c

# testes rodam com java 21 (no mac acha pelo java_home, senão usa o JAVA_HOME)
JAVA21 := $(shell /usr/libexec/java_home -v 21 2>/dev/null)
ifneq ($(JAVA21),)
export JAVA_HOME := $(JAVA21)
endif

SERVICOS := discovery-server api-gateway acompanhamento notificacoes
MVN := ./mvnw -B -ntp

help: ## lista os alvos
	@grep -E '^[a-z0-9-]+:.*##' $(MAKEFILE_LIST) | awk 'BEGIN{FS=":.*## "}{printf "  make %-12s %s\n", $$1, $$2}'

.env:
	cp .env.example .env

up: .env ## sobe tudo (build + espera os healthchecks; a 1ª instalação do Moodle leva ~2 min)
	docker compose up -d --build --wait

down: ## para e remove os contêineres (mantém os volumes)
	docker compose down

clean: ## equivale a "docker compose down -v": remove contêineres E volumes (apaga Moodle, bancos e e-mails)
	docker compose down -v

seed: .env ## configura o Moodle (webservice, token, disciplina, alunos, tarefas) e grava o token no .env
	scripts/seed_moodle.sh $(ARGS)

reset-dates: ## reposiciona os prazos de T1..T5 relativos a agora (antes de uma demo)
	scripts/seed_moodle.sh --reset-dates

reset: ## zera notificações, filas e Mailpit (não mexe no Moodle)
	scripts/reset_notificacoes.sh

test: ## testes unitários/integração dos 4 projetos Spring (requer Java 21; não precisa do compose)
	@for s in $(SERVICOS); do \
	  echo "== $$s"; \
	  (cd $$s && $(MVN) test 2>&1) | grep -E '^\[INFO\] (Tests run:|BUILD)|^\[ERROR\]' | grep -v ' -- in '; \
	done

e2e: .env ## testes E2E dos 4 critérios (sobe o compose, roda o seed; zera as notificações)
	cd tests/e2e && $(MVN) test

demo: ## roteiro da apresentação passo a passo
	scripts/demo.sh

logs: ## acompanha os logs dos microsserviços
	docker compose logs -f acompanhamento notificacoes

ps: ## estado dos contêineres
	docker compose ps
