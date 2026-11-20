package br.pucrs.es2.notificacoes.notificacao;

import static br.pucrs.es2.notificacoes.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

// roda a migration do flyway no h2 em modo postgres
@DataJpaTest
class NotificacaoRepositoryTest {

	@Autowired
	private NotificacaoRepository repository;

	@Test
	void idempotencyKeyEUnica() {
		repository.saveAndFlush(new Notificacao("2:1:4", "a@example.com", "T1", "{}", NOW));
		assertThatThrownBy(() -> repository.saveAndFlush(new Notificacao("2:1:4", "a@example.com", "T1", "{}", NOW)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void buscaPorChaveEStatus() {
		var n = new Notificacao("2:1:5", "b@example.com", "T1", "{}", NOW);
		n.marcarEnviado(NOW);
		repository.saveAndFlush(n);
		assertThat(repository.findByIdempotencyKey("2:1:5")).get().extracting(Notificacao::getStatus).isEqualTo(StatusNotificacao.ENVIADO);
		assertThat(repository.findByStatusOrderByCriadoEmDescIdDesc(StatusNotificacao.ENVIADO)).hasSize(1);
		assertThat(repository.findByStatusOrderByCriadoEmDescIdDesc(StatusNotificacao.FALHOU)).isEmpty();
	}

}
