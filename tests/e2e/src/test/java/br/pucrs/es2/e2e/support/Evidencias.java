package br.pucrs.es2.e2e.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;

public final class Evidencias {

	public static final Path DIR = Env.ROOT.resolve("tests/e2e/evidencias");
	private static final JsonMapper JSON = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build();

	public static void salvar(String nome, Object conteudo) {
		String texto = conteudo instanceof String s ? s : JSON.writeValueAsString(conteudo);
		try {
			Files.createDirectories(DIR);
			Files.writeString(DIR.resolve(nome), texto.endsWith("\n") ? texto : texto + "\n");
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	public static String jsonLines(List<JsonNode> linhas) {
		return linhas.stream().map(JsonNode::toString).collect(Collectors.joining("\n"));
	}

	private Evidencias() {
	}

}
