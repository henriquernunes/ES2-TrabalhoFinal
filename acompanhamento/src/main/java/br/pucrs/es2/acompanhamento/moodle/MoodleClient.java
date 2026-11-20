package br.pucrs.es2.acompanhamento.moodle;

import br.pucrs.es2.acompanhamento.config.AcompanhamentoProperties;
import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
public class MoodleClient {

	static final String REST_PATH = "/webservice/rest/server.php";

	private final RestClient restClient;
	private final JsonMapper jsonMapper;
	private final String token;

	public MoodleClient(RestClient moodleRestClient, JsonMapper jsonMapper, AcompanhamentoProperties props) {
		this.restClient = moodleRestClient;
		this.jsonMapper = jsonMapper;
		this.token = props.moodle().token() == null ? "" : props.moodle().token();
	}

	public boolean isConfigured() {
		return !token.isBlank();
	}

	public <T> T call(String wsfunction, Map<String, ?> params, Class<T> type) {
		JsonNode node = callRaw(wsfunction, params);
		try {
			return jsonMapper.treeToValue(node, type);
		} catch (JacksonException e) {
			throw new MoodleApiException("resposta do Moodle em formato inesperado: " + e.getOriginalMessage(), wsfunction, "invalidresponse", e);
		}
	}

	JsonNode callRaw(String wsfunction, Map<String, ?> params) {
		if (!isConfigured()) {
			throw new MoodleApiException("MOODLE_TOKEN não configurado (rode `make seed`)", wsfunction, "notoken");
		}
		var all = new LinkedHashMap<String, Object>(params);
		all.put("wstoken", token);
		all.put("wsfunction", wsfunction);
		all.put("moodlewsrestformat", "json");

		String body;
		try {
			body = restClient.post()
					.uri(REST_PATH)
					.contentType(MediaType.APPLICATION_FORM_URLENCODED)
					.body(MoodleParams.encode(all))
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException e) {
			throw new MoodleApiException("Moodle respondeu HTTP " + e.getStatusCode().value(), wsfunction, "http", e);
		} catch (ResourceAccessException e) {
			if (e.getCause() instanceof SocketTimeoutException || e.getCause() instanceof HttpTimeoutException) {
				throw new MoodleApiException("timeout ao chamar o Moodle (" + e.getCause().getMessage() + ")", wsfunction, "timeout", e);
			}
			throw new MoodleApiException("Moodle inacessível (" + e.getMostSpecificCause() + ")", wsfunction, "network", e);
		} catch (RestClientException e) {
			throw new MoodleApiException(e.getMessage(), wsfunction, "unknown", e);
		}

		JsonNode node;
		try {
			node = jsonMapper.readTree(body == null ? "" : body);
		} catch (JacksonException e) {
			throw new MoodleApiException("resposta inesperada do Moodle (não é JSON)", wsfunction, "invalidresponse", e);
		}
		if (node == null || node.isMissingNode() || !(node.isObject() || node.isArray())) {
			throw new MoodleApiException("resposta inesperada do Moodle (não é JSON)", wsfunction, "invalidresponse");
		}
		// o moodle devolve erro com http 200, só dá pra saber pelo campo exception
		if (node.isObject() && node.has("exception")) {
			String errorcode = node.path("errorcode").asString();
			throw new MoodleApiException(errorcode + ": " + node.path("message").asString(), wsfunction, errorcode);
		}
		return node;
	}

}
