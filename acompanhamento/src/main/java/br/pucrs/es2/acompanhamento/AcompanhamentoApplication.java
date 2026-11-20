package br.pucrs.es2.acompanhamento;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class AcompanhamentoApplication {

	public static void main(String[] args) {
		// sem isso o httpclient do jdk não deixa mandar o header host (ver MoodleHttpConfig)
		System.setProperty("jdk.httpclient.allowRestrictedHeaders", "host");
		SpringApplication.run(AcompanhamentoApplication.class, args);
	}

}
