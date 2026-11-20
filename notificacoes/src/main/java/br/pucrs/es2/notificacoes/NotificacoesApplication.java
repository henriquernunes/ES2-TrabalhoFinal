package br.pucrs.es2.notificacoes;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class NotificacoesApplication {

	public static void main(String[] args) {
		SpringApplication.run(NotificacoesApplication.class, args);
	}

}
