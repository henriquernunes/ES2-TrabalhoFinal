package br.pucrs.es2.acompanhamento.moodle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import br.pucrs.es2.acompanhamento.Fixtures;
import br.pucrs.es2.acompanhamento.config.AcompanhamentoProperties;
import br.pucrs.es2.acompanhamento.config.MoodleHttpConfig;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

class MoodleClientTest {

	private MockRestServiceServer server;

	private MoodleClient client(AcompanhamentoProperties props) {
		RestClient.Builder builder = MoodleHttpConfig.configure(RestClient.builder(), props.moodle());
		server = MockRestServiceServer.bindTo(builder).build();
		return new MoodleClient(builder.build(), JsonMapper.builder().build(), props);
	}

	@Test
	void enviaFormUrlencodedComTokenFuncaoFormatoJsonEHostPublico() {
		var client = client(Fixtures.props());
		server.expect(requestTo("http://moodle:8080/webservice/rest/server.php"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("Host", "localhost:8080"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().string("courseids%5B0%5D=2&wstoken=token-de-teste&wsfunction=mod_assign_get_assignments&moodlewsrestformat=json"))
				.andRespond(withSuccess("{\"courses\":[]}", MediaType.APPLICATION_JSON));

		client.callRaw("mod_assign_get_assignments", Map.of("courseids", List.of(2)));
		server.verify();
	}

	@Test
	void naoSobrescreveHostQuandoUrlsCoincidem() {
		var client = client(Fixtures.props("t", "http://localhost:8080"));
		server.expect(requestTo("http://localhost:8080/webservice/rest/server.php"))
				.andExpect(request -> assertThat(request.getHeaders().get("Host")).isNull())
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
		client.callRaw("x", Map.of());
		server.verify();
	}

	@Test
	void respostaComExceptionViraMoodleApiException() {
		var client = client(Fixtures.props());
		server.expect(requestTo("http://moodle:8080/webservice/rest/server.php")).andRespond(withSuccess(
				"{\"exception\":\"moodle_exception\",\"errorcode\":\"invalidtoken\",\"message\":\"Invalid token\"}",
				MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.callRaw("mod_assign_get_assignments", Map.of()))
				.isInstanceOf(MoodleApiException.class)
				.hasMessage("invalidtoken: Invalid token")
				.extracting("errorcode", "wsfunction").containsExactly("invalidtoken", "mod_assign_get_assignments");
	}

	@Test
	void timeoutViraMoodleApiExceptionTimeout() {
		var client = client(Fixtures.props());
		server.expect(requestTo("http://moodle:8080/webservice/rest/server.php")).andRespond(request -> {
			throw new SocketTimeoutException("Read timed out");
		});
		assertThatThrownBy(() -> client.callRaw("x", Map.of())).extracting("errorcode").isEqualTo("timeout");
	}

	@Test
	void rejeitaRespostaQueNaoEJson() {
		var client = client(Fixtures.props());
		server.expect(requestTo("http://moodle:8080/webservice/rest/server.php"))
				.andRespond(withSuccess("<html>Redirect</html>", MediaType.TEXT_HTML));
		assertThatThrownBy(() -> client.callRaw("x", Map.of())).extracting("errorcode").isEqualTo("invalidresponse");
	}

	@Test
	void falhaSemChamarARedeQuandoNaoHaToken() {
		var client = client(Fixtures.props("", "http://moodle:8080"));
		assertThatThrownBy(() -> client.callRaw("x", Map.of())).extracting("errorcode").isEqualTo("notoken");
		server.verify();
	}

}
