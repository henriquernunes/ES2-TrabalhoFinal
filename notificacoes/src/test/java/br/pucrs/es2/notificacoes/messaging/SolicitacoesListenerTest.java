package br.pucrs.es2.notificacoes.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucrs.es2.notificacoes.Fixtures;
import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.notificacao.NotificacoesService;
import br.pucrs.es2.notificacoes.notificacao.NotificacoesService.FalhaEnvioException;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validation;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class SolicitacoesListenerTest {

	private static final String KEY = "2:1:4";

	private final NotificacoesService service = mock(NotificacoesService.class);
	private final RabbitPublisher publisher = mock(RabbitPublisher.class);
	private final Channel channel = mock(Channel.class);
	private final SolicitacoesListener listener = new SolicitacoesListener(service, publisher,
			JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build(),
			Validation.buildDefaultValidatorFactory().getValidator(),
			new NotificacoesProperties("x@example.com", 5, Duration.ofSeconds(5)));

	private static Message message(String body, Integer retryCount) {
		var props = new MessageProperties();
		props.setDeliveryTag(7);
		if (retryCount != null) {
			props.setHeader(Filas.HEADER_RETRY_COUNT, retryCount);
		}
		props.setHeader("x-death", "do broker");
		return new Message(body.getBytes(StandardCharsets.UTF_8), props);
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> publicadoEm(String fila) {
		var headers = ArgumentCaptor.forClass(Map.class);
		verify(publisher).publicar(eq(fila), any(), headers.capture(), any());
		return headers.getValue();
	}

	@Test
	void sucessoDaAck() throws Exception {
		listener.onMessage(message(Fixtures.json(KEY), null), channel);
		verify(service).processar(Fixtures.solicitacao(KEY), Fixtures.json(KEY));
		verify(channel).basicAck(7, false);
		verify(publisher, never()).publicar(anyString(), any(), anyMap(), any());
	}

	@Test
	void primeiraFalhaVaiParaRetry5sComContador1() throws Exception {
		when(service.processar(any(), any())).thenThrow(new FalhaEnvioException("smtp fora", null));
		listener.onMessage(message(Fixtures.json(KEY), null), channel);

		var headers = publicadoEm("reengajamento.solicitacoes.retry.5000ms");
		assertThat(headers).containsEntry("x-retry-count", 1).containsEntry("x-last-error", "smtp fora").doesNotContainKey("x-death");
		verify(channel).basicAck(7, false);
	}

	@Test
	void terceiraFalhaUsa20s() throws Exception {
		when(service.processar(any(), any())).thenThrow(new FalhaEnvioException("smtp fora", null));
		listener.onMessage(message(Fixtures.json(KEY), 2), channel);
		assertThat(publicadoEm("reengajamento.solicitacoes.retry.20000ms")).containsEntry("x-retry-count", 3);
	}

	@Test
	void esgotadasAsTentativasVaiParaDlqEMarcaFalhou() throws Exception {
		when(service.processar(any(), any())).thenThrow(new FalhaEnvioException("smtp fora", null));
		listener.onMessage(message(Fixtures.json(KEY), 5), channel);
		assertThat(publicadoEm("reengajamento.dlq")).containsEntry("x-retry-count", 5);
		verify(service).marcarFalhou(KEY, "smtp fora");
		verify(channel).basicAck(7, false);
	}

	@Test
	void jsonMalformadoVaiDiretoParaDlq() throws Exception {
		listener.onMessage(message("{nao é json", null), channel);
		assertThat(publicadoEm("reengajamento.dlq").get("x-last-error").toString()).startsWith("mensagem inválida: JSON");
		verify(service, never()).processar(any(), any());
		verify(channel).basicAck(7, false);
	}

	@Test
	void camposObrigatoriosAusentesVaoParaDlq() throws Exception {
		listener.onMessage(message("{\"idempotency_key\":\"k\",\"user_email\":\"nao-e-email\"}", null), channel);
		assertThat(publicadoEm("reengajamento.dlq").get("x-last-error").toString()).contains("userEmail", "assignName");
		verify(service, never()).processar(any(), any());
	}

	@Test
	void seOEncaminhamentoFalharDevolveComNack() throws Exception {
		when(service.processar(any(), any())).thenThrow(new FalhaEnvioException("smtp fora", null));
		doThrow(new RuntimeException("broker recusou")).when(publisher).publicar(anyString(), any(), anyMap(), any());
		listener.onMessage(message(Fixtures.json(KEY), null), channel);
		verify(channel).basicNack(7, false, true);
		verify(channel, never()).basicAck(7, false);
	}

}
