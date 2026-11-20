package br.pucrs.es2.notificacoes.notificacao;

import static br.pucrs.es2.notificacoes.Fixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucrs.es2.notificacoes.Fixtures;
import br.pucrs.es2.notificacoes.mail.MailService;
import br.pucrs.es2.notificacoes.messaging.RabbitPublisher;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

class NotificacoesServiceTest {

	private final NotificacaoRepository repository = mock(NotificacaoRepository.class);
	private final MailService mail = mock(MailService.class);
	private final RabbitPublisher publisher = mock(RabbitPublisher.class);
	private final NotificacoesService service = new NotificacoesService(repository, mail, publisher, Clock.fixed(NOW, ZoneOffset.UTC));
	private final String key = "2:1:4";

	@BeforeEach
	void setUp() {
		when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
	}

	private Notificacao existente(StatusNotificacao status) {
		var n = new Notificacao(key, "aluno1@example.com", "T1", "{}", NOW);
		n.registrarTentativa(NOW);
		if (status == StatusNotificacao.ENVIADO) {
			n.marcarEnviado(NOW);
		} else if (status == StatusNotificacao.FALHOU) {
			n.marcarFalhou("smtp", NOW);
		}
		when(repository.findByIdempotencyKey(key)).thenReturn(Optional.of(n));
		return n;
	}

	@Test
	void novaChaveEnviaEMarcaEnviado() {
		when(repository.findByIdempotencyKey(key)).thenReturn(Optional.empty());
		assertThat(service.processar(Fixtures.solicitacao(key), "{json}")).isEqualTo(NotificacoesService.Resultado.ENVIADO);
		verify(mail).enviarLembrete(Fixtures.solicitacao(key));
		var captor = org.mockito.ArgumentCaptor.forClass(Notificacao.class);
		verify(repository, org.mockito.Mockito.atLeastOnce()).save(captor.capture());
		var salvo = captor.getValue();
		assertThat(salvo.getStatus()).isEqualTo(StatusNotificacao.ENVIADO);
		assertThat(salvo.getTentativas()).isEqualTo(1);
		assertThat(salvo.getPayload()).isEqualTo("{json}");
		assertThat(salvo.getEnviadoEm()).isEqualTo(NOW);
	}

	@Test
	void chaveJaEnviadaNaoReenvia() {
		existente(StatusNotificacao.ENVIADO);
		assertThat(service.processar(Fixtures.solicitacao(key), "{}")).isEqualTo(NotificacoesService.Resultado.DUPLICADA);
		verify(mail, never()).enviarLembrete(any());
		verify(repository, never()).save(any());
	}

	@Test
	void falhaDeSmtpRegistraErroETentativaELanca() {
		var n = existente(StatusNotificacao.PENDENTE);
		doThrow(new MailSendException("Connection refused")).when(mail).enviarLembrete(any());

		assertThatThrownBy(() -> service.processar(Fixtures.solicitacao(key), "{}"))
				.isInstanceOf(NotificacoesService.FalhaEnvioException.class)
				.hasMessageContaining("Connection refused");
		assertThat(n.getStatus()).isEqualTo(StatusNotificacao.PENDENTE);
		assertThat(n.getTentativas()).isEqualTo(2);
		assertThat(n.getUltimoErro()).contains("MailSendException: Connection refused");
	}

	@Test
	void falhouEReabertoPorNovaSolicitacao() {
		var n = existente(StatusNotificacao.FALHOU);
		service.processar(Fixtures.solicitacao(key), "{novo}");
		assertThat(n.getStatus()).isEqualTo(StatusNotificacao.ENVIADO);
		assertThat(n.getPayload()).isEqualTo("{novo}");
		assertThat(n.getUltimoErro()).isNull();
	}

	@Test
	void marcarFalhou() {
		var n = existente(StatusNotificacao.PENDENTE);
		service.marcarFalhou(key, "esgotou");
		assertThat(n.getStatus()).isEqualTo(StatusNotificacao.FALHOU);
		assertThat(n.getUltimoErro()).isEqualTo("esgotou");
	}

	@Test
	void reprocessarRepublicaOPayloadMesmoSeEnviado() {
		existente(StatusNotificacao.ENVIADO);
		var resposta = service.reprocessar(key);
		verify(publisher).publicar(eq("reengajamento.solicitacoes"), eq("{}".getBytes(StandardCharsets.UTF_8)), anyMap(), eq(key));
		assertThat(resposta.statusAtual()).isEqualTo(StatusNotificacao.ENVIADO);
		assertThat(resposta.mensagem()).contains("vai ignorar");
	}

	@Test
	void reprocessarChaveInexistenteDa404() {
		when(repository.findByIdempotencyKey("x")).thenReturn(Optional.empty());
		assertThatThrownBy(() -> service.reprocessar("x")).isInstanceOf(NotificacaoNaoEncontradaException.class);
	}

}
