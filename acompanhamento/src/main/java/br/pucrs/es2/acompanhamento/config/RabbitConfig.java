package br.pucrs.es2.acompanhamento.config;

import br.pucrs.es2.acompanhamento.messaging.Filas;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.json.JsonMapper;

@Configuration
public class RabbitConfig {

	// tem que ser declarada igual no notificacoes
	@Bean
	Queue solicitacoesQueue() {
		return QueueBuilder.durable(Filas.SOLICITACOES).build();
	}

	@Bean
	MessageConverter jsonMessageConverter(JsonMapper jsonMapper) {
		return new JacksonJsonMessageConverter(jsonMapper);
	}

}
