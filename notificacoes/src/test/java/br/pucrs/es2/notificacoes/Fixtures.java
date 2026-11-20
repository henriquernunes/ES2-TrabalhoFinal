package br.pucrs.es2.notificacoes;

import br.pucrs.es2.notificacoes.notificacao.dto.SolicitacaoLembrete;
import java.time.Instant;

public final class Fixtures {

	public static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
	// 15:30 utc = 12:30 em brasília (sábado)
	public static final long DUE = Instant.parse("2026-11-21T15:30:00Z").getEpochSecond();

	public static SolicitacaoLembrete solicitacao(String key) {
		return new SolicitacaoLembrete(key, 2L, "Engenharia de Software II", 1L, 2L, "T1 - Casos de uso",
				Instant.ofEpochSecond(DUE).toString(), DUE, 4L, "Ana Souza", "aluno1@example.com",
				"http://localhost:8080/mod/assign/view.php?id=2", NOW.toString());
	}

	public static String json(String key) {
		return """
				{"idempotency_key":"%s","course_id":2,"course_name":"Engenharia de Software II","assign_id":1,"cmid":2,\
				"assign_name":"T1 - Casos de uso","duedate":"2026-11-21T15:30:00Z","duedate_epoch":%d,"user_id":4,\
				"user_fullname":"Ana Souza","user_email":"aluno1@example.com",\
				"assign_url":"http://localhost:8080/mod/assign/view.php?id=2","created_at":"2026-11-20T12:00:00Z"}""".formatted(key, DUE);
	}

	private Fixtures() {
	}

}
