package br.pucrs.es2.acompanhamento.reengajamento;

import static br.pucrs.es2.acompanhamento.Fixtures.ASSIGNMENTS;
import static br.pucrs.es2.acompanhamento.Fixtures.NOW;
import static br.pucrs.es2.acompanhamento.Fixtures.NOW_EPOCH;
import static br.pucrs.es2.acompanhamento.Fixtures.STUDENTS;
import static org.assertj.core.api.Assertions.assertThat;

import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Assignment;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Course;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ReminderRulesTest {

	private static final Duration JANELA = Duration.ofHours(24);

	@ParameterizedTest(name = "{0} -> {2}")
	@CsvSource({
			"sem prazo (0),                     ZERO,  false",
			"já vencida,                        -1,    false",
			"vence exatamente agora,            0,     false",
			"vence em 1 s,                      1,     true",
			"vence em 12 h,                     43200, true",
			"vence exatamente no fim da janela, 86400, true",
			"vence 1 s depois da janela,        86401, false",
			"vence em 3 dias,                   259200, false" })
	void isDueWithinWindow(String label, String offset, boolean expected) {
		long duedate = "ZERO".equals(offset) ? 0 : NOW_EPOCH + Long.parseLong(offset);
		assertThat(ReminderRules.isDueWithinWindow(duedate, NOW, JANELA)).isEqualTo(expected);
	}

	@Test
	void respeitaJanelaConfigurada() {
		assertThat(ReminderRules.isDueWithinWindow(NOW_EPOCH + 72 * 3600, NOW, Duration.ofHours(96))).isTrue();
	}

	@Test
	void selecionaSoT1eT2() {
		assertThat(ReminderRules.assignmentsInWindow(ASSIGNMENTS, NOW, JANELA)).extracting(Assignment::name).containsExactly("T1", "T2");
	}

	@Test
	void chaveEUrl() {
		assertThat(ReminderRules.idempotencyKey(2, 7, 9)).isEqualTo("2:7:9");
		assertThat(ReminderRules.assignUrl("http://localhost:8080/", 15)).isEqualTo("http://localhost:8080/mod/assign/view.php?id=15");
	}

	@Test
	void umItemPorParPendenteSemQuemJaEnviou() {
		var result = build();
		// ana (id 4) já enviou a t2
		assertThat(result).extracting(SolicitacaoLembrete::idempotencyKey).containsExactly("2:1:4", "2:1:5", "2:1:6", "2:2:5", "2:2:6");
	}

	@Test
	void preencheOContratoDaMensagem() {
		long due = NOW_EPOCH + 12 * 3600;
		assertThat(build().getFirst()).isEqualTo(new SolicitacaoLembrete("2:1:4", 2, "Engenharia de Software II", 1, 11, "T1",
				Instant.ofEpochSecond(due).toString(), due, 4, "Ana Souza", "aluno1@example.com",
				"http://localhost:8080/mod/assign/view.php?id=11", NOW.toString()));
	}

	private static List<SolicitacaoLembrete> build() {
		var inWindow = ReminderRules.assignmentsInWindow(ASSIGNMENTS, NOW, JANELA);
		return ReminderRules.buildSolicitacoes(new Course(2, "Engenharia de Software II", ASSIGNMENTS), inWindow, STUDENTS,
				Map.of(1L, Set.of(), 2L, Set.of(4L)), "http://localhost:8080", NOW);
	}

}
