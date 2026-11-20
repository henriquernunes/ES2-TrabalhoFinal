package br.pucrs.es2.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

// lê o .env da raiz (o surefire roda com a raiz como diretório)
public final class Env {

	public static final Path ROOT = Path.of("").toAbsolutePath();
	private static final Pattern LINE = Pattern.compile("^([A-Z0-9_]+)=(.*)$");
	private static final Map<String, String> VALUES = read();

	public static String get(String key, String fallback) {
		return VALUES.getOrDefault(key, fallback);
	}

	public static String gateway() {
		return "http://localhost:" + get("GATEWAY_PORT", "9000");
	}

	public static String mailpit() {
		return "http://localhost:8025";
	}

	private static Map<String, String> read() {
		var env = new HashMap<String, String>();
		try {
			for (String line : Files.readAllLines(ROOT.resolve(".env"))) {
				var m = LINE.matcher(line);
				if (m.matches()) {
					env.put(m.group(1), m.group(2).replaceAll("^\"(.*)\"$", "$1"));
				}
			}
		} catch (IOException e) {
			throw new UncheckedIOException("não achei o .env em " + ROOT + " (rode `make e2e` na raiz)", e);
		}
		return env;
	}

	private Env() {
	}

}
