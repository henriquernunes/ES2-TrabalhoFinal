package br.pucrs.es2.acompanhamento.config;

import java.net.URI;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class MoodleHttpConfig {

	// não segue redirect: se o moodle devolver 303 é porque o host tá errado
	@Bean
	RestClient moodleRestClient(AcompanhamentoProperties props) {
		var timeout = props.moodle().timeout();
		var httpClient = HttpClient.newBuilder()
				.connectTimeout(timeout)
				.followRedirects(HttpClient.Redirect.NEVER)
				.build();
		var requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		return configure(RestClient.builder().requestFactory(requestFactory), props.moodle()).build();
	}

	// separado pra conseguir usar no teste com o MockRestServiceServer
	public static RestClient.Builder configure(RestClient.Builder builder, AcompanhamentoProperties.Moodle moodle) {
		var internal = URI.create(moodle.url());
		var publicUri = URI.create(moodle.publicUrl());
		builder.baseUrl(moodle.url());
		// o moodle redireciona se o host não for o do wwwroot, e dentro do compose a gente chama moodle:8080
		if (!internal.getAuthority().equals(publicUri.getAuthority())) {
			builder.defaultHeader(HttpHeaders.HOST, publicUri.getAuthority());
		}
		return builder;
	}

}
