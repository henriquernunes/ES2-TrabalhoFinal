package br.pucrs.es2.notificacoes.messaging;

import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.notificacao.NotificacoesService;
import br.pucrs.es2.notificacoes.notificacao.dto.SolicitacaoLembrete;
import com.rabbitmq.client.Channel;
import jakarta.validation.Validator;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

// ack manual: só dá ack depois que a msg foi pra retry/dlq com confirm.
// se não conseguir encaminhar, nack com requeue
@Component
public class SolicitacoesListener {

	private static final Logger log = LoggerFactory.getLogger(SolicitacoesListener.class);
	private static final int MAX_HEADER_ERRO = 500;

	private final NotificacoesService service;
	private final RabbitPublisher publisher;
	private final JsonMapper jsonMapper;
	private final Validator validator;
	private final NotificacoesProperties props;

	public SolicitacoesListener(NotificacoesService service, RabbitPublisher publisher, JsonMapper jsonMapper, Validator validator,
			NotificacoesProperties props) {
		this.service = service;
		this.publisher = publisher;
		this.jsonMapper = jsonMapper;
		this.validator = validator;
		this.props = props;
	}

	@RabbitListener(queues = Filas.SOLICITACOES)
	public void onMessage(Message message, Channel channel) throws IOException {
		long tag = message.getMessageProperties().getDeliveryTag();
		try {
			rotear(message);
			channel.basicAck(tag, false);
		} catch (RuntimeException e) {
			// não conseguiu mandar pra retry/dlq, devolve pra fila pra não perder
			log.atError().addKeyValue("err", e.getMessage()).log("falha ao encaminhar mensagem; devolvendo para a fila");
			channel.basicNack(tag, false, true);
		}
	}

	void rotear(Message message) {
		byte[] corpo = message.getBody();
		String payload = new String(corpo, StandardCharsets.UTF_8);
		SolicitacaoLembrete solicitacao;
		try {
			solicitacao = jsonMapper.readValue(corpo, SolicitacaoLembrete.class);
		} catch (JacksonException e) {
			moverParaDlq(message, null, "mensagem inválida: JSON não corresponde ao contrato (" + e.getOriginalMessage() + ")");
			return;
		}
		var violacoes = validator.validate(solicitacao);
		if (!violacoes.isEmpty()) {
			String erro = violacoes.stream().map(v -> v.getPropertyPath() + " " + v.getMessage()).sorted().collect(Collectors.joining("; "));
			moverParaDlq(message, solicitacao.idempotencyKey(), "mensagem inválida: " + erro);
			return;
		}

		try {
			service.processar(solicitacao, payload);
		} catch (RuntimeException e) {
			agendarNovaTentativa(message, solicitacao.idempotencyKey(), e.getMessage());
		}
	}

	private void agendarNovaTentativa(Message message, String key, String erro) {
		int retryCount = retryCount(message);
		switch (RetryPolicy.decidir(retryCount, props.maxRetries(), props.retryBaseDelay())) {
			case RetryPolicy.NovaTentativa n -> {
				publisher.publicar(n.fila(), message.getBody(), headers(message, n.tentativa(), erro), key);
				log.atWarn().addKeyValue("idempotency_key", key).addKeyValue("retry", n.tentativa())
						.addKeyValue("atraso_ms", n.atraso().toMillis()).addKeyValue("err", erro).log("nova tentativa agendada");
			}
			case RetryPolicy.MoverParaDlq d -> {
				moverParaDlq(message, key, erro);
				service.marcarFalhou(key, erro);
			}
		}
	}

	private void moverParaDlq(Message message, String key, String erro) {
		publisher.publicar(Filas.DLQ, message.getBody(), headers(message, retryCount(message), erro), key);
		log.atError().addKeyValue("idempotency_key", key).addKeyValue("err", erro).log("mensagem enviada para a DLQ");
	}

	static int retryCount(Message message) {
		Object value = message.getMessageProperties().getHeaders().get(Filas.HEADER_RETRY_COUNT);
		if (value instanceof Number n) {
			return n.intValue();
		}
		try {
			return value == null ? 0 : Integer.parseInt(value.toString());
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	private static Map<String, Object> headers(Message message, int retryCount, String erro) {
		var headers = new HashMap<String, Object>();
		message.getMessageProperties().getHeaders().forEach((k, v) -> {
			// descarta os x-death que o broker coloca
			if (!k.startsWith("x-death") && !k.startsWith("x-first-death") && !k.startsWith("x-last-death")) {
				headers.put(k, v);
			}
		});
		headers.put(Filas.HEADER_RETRY_COUNT, retryCount);
		String texto = erro == null ? "" : erro;
		headers.put(Filas.HEADER_LAST_ERROR, texto.length() <= MAX_HEADER_ERRO ? texto : texto.substring(0, MAX_HEADER_ERRO));
		return headers;
	}

}
