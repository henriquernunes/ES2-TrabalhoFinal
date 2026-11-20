package br.pucrs.es2.acompanhamento.messaging;

import br.pucrs.es2.acompanhamento.reengajamento.SolicitacaoLembrete;
import java.time.Duration;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

// publica persistente e espera o confirm do broker
@Component
public class SolicitacaoPublisher {

	private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

	private final RabbitTemplate rabbitTemplate;

	public SolicitacaoPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publicar(SolicitacaoLembrete solicitacao) {
		rabbitTemplate.invoke(ops -> {
			ops.convertAndSend("", Filas.SOLICITACOES, solicitacao, message -> {
				message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
				message.getMessageProperties().setMessageId(solicitacao.idempotencyKey());
				return message;
			});
			ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
			return null;
		});
	}

}
