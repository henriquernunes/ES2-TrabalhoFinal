package br.pucrs.es2.e2e.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

public final class Shell {

	private static final JsonMapper JSON = JsonMapper.builder().build();

	// retry mais rápido pro teste de falha não demorar
	public static final Map<String, String> FAST_RETRY_ENV = Map.of("SMTP_TIMEOUT_MS", "2000", "RETRY_BASE_DELAY_MS", "3000", "MAX_RETRIES", "5");

	public static String sh(String command, Map<String, String> extraEnv) {
		var pb = new ProcessBuilder("bash", "-c", command).directory(Env.ROOT.toFile()).redirectErrorStream(true);
		pb.environment().putAll(extraEnv);
		try {
			var process = pb.start();
			String out = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
			int code = process.waitFor();
			if (code != 0) {
				throw new IllegalStateException("`" + command + "` saiu com " + code + ":\n" + out);
			}
			return out;
		} catch (IOException e) {
			throw new IllegalStateException(e);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	public static String sh(String command) {
		return sh(command, Map.of());
	}

	public static String compose(String args) {
		return sh("docker compose " + args);
	}

	public static void upAndSeed() {
		compose("up -d --build --wait");
		sh("scripts/seed_moodle.sh --reset-dates");
	}

	// cuidado: apaga as notificações, filas e emails do ambiente local
	public static void resetNotificacoes() {
		sh("scripts/reset_notificacoes.sh", FAST_RETRY_ENV);
	}

	public static List<JsonNode> logs(String service, String since) {
		var lines = new ArrayList<JsonNode>();
		for (String line : compose("logs --no-color --no-log-prefix --since " + since + " " + service).split("\n")) {
			if (line.startsWith("{")) {
				try {
					lines.add(JSON.readTree(line));
				} catch (RuntimeException ignored) {
					// ignora o que não for json
				}
			}
		}
		return lines;
	}

	private Shell() {
	}

}
