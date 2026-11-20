package br.pucrs.es2.acompanhamento;

import br.pucrs.es2.acompanhamento.config.AcompanhamentoProperties;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Assignment;
import br.pucrs.es2.acompanhamento.moodle.dto.Student;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

public final class Fixtures {

	public static final Instant NOW = Instant.parse("2026-11-20T12:00:00Z");
	public static final long NOW_EPOCH = NOW.getEpochSecond();
	private static final long HOUR = 3600;

	// igual ao seed: t1/t2 em 12h, t3 em 3 dias, t4 vencida, t5 sem prazo
	public static final List<Assignment> ASSIGNMENTS = List.of(
			new Assignment(1, 11, 2, "T1", NOW_EPOCH + 12 * HOUR),
			new Assignment(2, 12, 2, "T2", NOW_EPOCH + 12 * HOUR),
			new Assignment(3, 13, 2, "T3", NOW_EPOCH + 72 * HOUR),
			new Assignment(4, 14, 2, "T4", NOW_EPOCH - 48 * HOUR),
			new Assignment(5, 15, 2, "T5", 0));

	public static final List<Student> STUDENTS = List.of(
			new Student(4, "Ana Souza", "aluno1@example.com"),
			new Student(5, "Bruno Lima", "aluno2@example.com"),
			new Student(6, "Carla Mendes", "aluno3@example.com"));

	public static AcompanhamentoProperties props(String token, String url) {
		return new AcompanhamentoProperties(2, 3600, 24,
				new AcompanhamentoProperties.Moodle(url, "http://localhost:8080", token, Duration.ofSeconds(1)));
	}

	public static AcompanhamentoProperties props() {
		return props("token-de-teste", "http://moodle:8080");
	}

	private Fixtures() {
	}

}
