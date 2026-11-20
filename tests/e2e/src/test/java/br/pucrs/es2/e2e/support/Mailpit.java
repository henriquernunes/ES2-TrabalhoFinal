package br.pucrs.es2.e2e.support;

import java.util.List;
import java.util.stream.StreamSupport;
import tools.jackson.databind.JsonNode;

public final class Mailpit {

	public record Email(String id, String assunto, List<String> para) {
	}

	public static List<Email> mensagens() {
		var res = Http.get(Env.mailpit() + "/api/v1/messages?limit=500");
		if (res.status() != 200) {
			throw new IllegalStateException("Mailpit HTTP " + res.status());
		}
		return StreamSupport.stream(res.body().path("messages").spliterator(), false)
				.map(m -> new Email(m.path("ID").asString(), m.path("Subject").asString(),
						StreamSupport.stream(m.path("To").spliterator(), false).map(t -> t.path("Address").asString()).toList()))
				.toList();
	}

	// filtra pelo prefixo da tarefa no assunto ("T1 -", "T2 -")
	public static List<Email> para(List<Email> mensagens, String email, String tarefa) {
		return mensagens.stream().filter(m -> m.para().contains(email) && m.assunto().contains("\"" + tarefa + " -")).toList();
	}

	public static JsonNode detalhe(String id) {
		return Http.get(Env.mailpit() + "/api/v1/message/" + id).body();
	}

	private Mailpit() {
	}

}
