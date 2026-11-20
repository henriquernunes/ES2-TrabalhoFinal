package br.pucrs.es2.notificacoes.config;

import br.pucrs.es2.notificacoes.messaging.Filas;
import br.pucrs.es2.notificacoes.messaging.RetryPolicy;
import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// solicitacoes -> falhou -> retry.<atraso>ms (ttl) -> volta pra solicitacoes
// depois de MAX_RETRIES vai pra dlq
@Configuration
public class RabbitConfig {

	@Bean
	Declarables topologia(NotificacoesProperties props) {
		List<Declarable> filas = new ArrayList<>();
		filas.add(QueueBuilder.durable(Filas.SOLICITACOES).build());
		filas.add(QueueBuilder.durable(Filas.DLQ).build());
		for (var atraso : RetryPolicy.atrasos(props.maxRetries(), props.retryBaseDelay())) {
			filas.add(QueueBuilder.durable(Filas.retryQueue(atraso))
					.ttl((int) atraso.toMillis())
					.deadLetterExchange("")
					.deadLetterRoutingKey(Filas.SOLICITACOES)
					.build());
		}
		return new Declarables(filas);
	}

}
