package br.pucrs.es2.notificacoes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class NotificacoesApplicationTests {

	@Autowired
	private Declarables topologia;

	@Test
	void declaraPrincipalDlqECincoFilasDeRetry() {
		var filas = topologia.getDeclarablesByType(Queue.class);
		assertThat(filas).extracting(Queue::getName).containsExactly("reengajamento.solicitacoes", "reengajamento.dlq",
				"reengajamento.solicitacoes.retry.5000ms", "reengajamento.solicitacoes.retry.10000ms",
				"reengajamento.solicitacoes.retry.20000ms", "reengajamento.solicitacoes.retry.40000ms",
				"reengajamento.solicitacoes.retry.80000ms");
		assertThat(filas.get(2).getArguments()).containsEntry("x-message-ttl", 5000).containsEntry("x-dead-letter-exchange", "")
				.containsEntry("x-dead-letter-routing-key", "reengajamento.solicitacoes");
	}

}
