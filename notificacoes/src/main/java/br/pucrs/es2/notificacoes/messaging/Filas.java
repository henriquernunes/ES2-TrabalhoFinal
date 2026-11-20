package br.pucrs.es2.notificacoes.messaging;

import java.time.Duration;

public final class Filas {

	// mesma declaração do acompanhamento
	public static final String SOLICITACOES = "reengajamento.solicitacoes";
	public static final String DLQ = "reengajamento.dlq";

	public static final String HEADER_RETRY_COUNT = "x-retry-count";
	public static final String HEADER_LAST_ERROR = "x-last-error";

	// uma fila por atraso (ttl na fila e não na mensagem, senão uma de 80s trava as de 5s).
	// o atraso vai no nome pra não dar conflito se mudar a config
	public static String retryQueue(Duration delay) {
		return SOLICITACOES + ".retry." + delay.toMillis() + "ms";
	}

	private Filas() {
	}

}
