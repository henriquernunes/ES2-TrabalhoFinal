package br.pucrs.es2.e2e.support;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class Http {

	public record Resposta(int status, JsonNode body) {
	}

	private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
	private static final JsonMapper JSON = JsonMapper.builder().build();

	public static Resposta get(String url) {
		return send(HttpRequest.newBuilder(URI.create(url)).GET());
	}

	public static Resposta post(String url) {
		return send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()));
	}

	public static String getText(String url) {
		try {
			return CLIENT.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(60)).GET().build(),
					HttpResponse.BodyHandlers.ofString()).body();
		} catch (IOException e) {
			throw new IllegalStateException(url + ": " + e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private static Resposta send(HttpRequest.Builder builder) {
		try {
			var res = CLIENT.send(builder.timeout(Duration.ofSeconds(60)).build(), HttpResponse.BodyHandlers.ofString());
			JsonNode body = res.body().isBlank() ? JSON.missingNode() : JSON.readTree(res.body());
			return new Resposta(res.statusCode(), body);
		} catch (IOException e) {
			throw new IllegalStateException(e.getMessage(), e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private Http() {
	}

}
