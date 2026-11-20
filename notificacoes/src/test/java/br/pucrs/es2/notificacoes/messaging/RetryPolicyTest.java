package br.pucrs.es2.notificacoes.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class RetryPolicyTest {

	private static final Duration BASE = Duration.ofSeconds(5);

	@Test
	void backoffExponencial() {
		assertThat(RetryPolicy.atrasos(5, BASE)).containsExactly(Duration.ofSeconds(5), Duration.ofSeconds(10), Duration.ofSeconds(20),
				Duration.ofSeconds(40), Duration.ofSeconds(80));
	}

	@Test
	void primeiraFalhaVaiParaARetryDe5s() {
		assertThat(RetryPolicy.decidir(0, 5, BASE))
				.isEqualTo(new RetryPolicy.NovaTentativa(1, Duration.ofSeconds(5), "reengajamento.solicitacoes.retry.5000ms"));
	}

	@Test
	void quintaNovaTentativaUsa80s() {
		assertThat(RetryPolicy.decidir(4, 5, BASE))
				.isEqualTo(new RetryPolicy.NovaTentativa(5, Duration.ofSeconds(80), "reengajamento.solicitacoes.retry.80000ms"));
	}

	@Test
	void aposMaxRetriesVaiParaADlq() {
		assertThat(RetryPolicy.decidir(5, 5, BASE)).isInstanceOf(RetryPolicy.MoverParaDlq.class);
		assertThat(RetryPolicy.decidir(0, 0, BASE)).isInstanceOf(RetryPolicy.MoverParaDlq.class);
	}

}
