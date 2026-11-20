package br.pucrs.es2.acompanhamento.reengajamento;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.CONFLICT, reason = "já existe um ciclo em execução")
public class CicloEmAndamentoException extends RuntimeException {

	public CicloEmAndamentoException() {
		super("já existe um ciclo em execução");
	}

}
