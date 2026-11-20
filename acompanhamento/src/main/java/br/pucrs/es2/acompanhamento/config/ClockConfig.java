package br.pucrs.es2.acompanhamento.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

	// bean pra dar pra fixar o relógio nos testes
	@Bean
	Clock clock() {
		return Clock.systemUTC();
	}

}
