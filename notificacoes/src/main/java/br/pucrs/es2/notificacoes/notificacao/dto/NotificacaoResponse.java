package br.pucrs.es2.notificacoes.notificacao.dto;

import br.pucrs.es2.notificacoes.notificacao.Notificacao;
import br.pucrs.es2.notificacoes.notificacao.StatusNotificacao;
import java.time.Instant;

public record NotificacaoResponse(
		String idempotencyKey,
		StatusNotificacao status,
		int tentativas,
		String ultimoErro,
		String userEmail,
		String assignName,
		Instant criadoEm,
		Instant atualizadoEm,
		Instant enviadoEm) {

	public static NotificacaoResponse of(Notificacao n) {
		return new NotificacaoResponse(n.getIdempotencyKey(), n.getStatus(), n.getTentativas(), n.getUltimoErro(),
				n.getUserEmail(), n.getAssignName(), n.getCriadoEm(), n.getAtualizadoEm(), n.getEnviadoEm());
	}

}
