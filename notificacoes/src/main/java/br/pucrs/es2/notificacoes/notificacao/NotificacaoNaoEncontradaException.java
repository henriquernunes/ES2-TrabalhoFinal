package br.pucrs.es2.notificacoes.notificacao;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class NotificacaoNaoEncontradaException extends ResponseStatusException {

	public NotificacaoNaoEncontradaException(String key) {
		super(HttpStatus.NOT_FOUND, "notificação " + key + " não encontrada");
	}

}
