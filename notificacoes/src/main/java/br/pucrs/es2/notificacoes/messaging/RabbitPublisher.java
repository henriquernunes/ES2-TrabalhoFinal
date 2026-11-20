package br.pucrs.es2.notificacoes.messaging;

import java.time.Duration;
import java.util.Map;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class RabbitPublisher {

	private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(10);

	private final RabbitTemplate rabbitTemplate;

	public RabbitPublisher(RabbitTemplate rabbitTemplate) {
		this.rabbitTemplate = rabbitTemplate;
	}

	public void publicar(String fila, byte[] corpo, Map<String, Object> headers, String messageId) {
		var builder = MessageBuilder.withBody(corpo)
				.setContentType("application/json")
				.setDeliveryMode(MessageDeliveryMode.PERSISTENT)
				.setMessageId(messageId);
		headers.forEach(builder::setHeader);
		Message message = builder.build();
		rabbitTemplate.invoke(ops -> {
			ops.send("", fila, message);
			ops.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
			return null;
		});
	}

}
