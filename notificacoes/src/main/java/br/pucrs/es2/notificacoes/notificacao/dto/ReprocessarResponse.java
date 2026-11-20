package br.pucrs.es2.notificacoes.notificacao.dto;

import br.pucrs.es2.notificacoes.notificacao.StatusNotificacao;

public record ReprocessarResponse(String idempotencyKey, StatusNotificacao statusAtual, String mensagem) {
}
