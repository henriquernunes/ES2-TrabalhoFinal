package br.pucrs.es2.notificacoes.mail;

import static org.assertj.core.api.Assertions.assertThat;

import br.pucrs.es2.notificacoes.Fixtures;
import br.pucrs.es2.notificacoes.notificacao.dto.SolicitacaoLembrete;
import org.junit.jupiter.api.Test;

class LembreteTemplateTest {

	@Test
	void formataPrazoEmPtBrNoHorarioDeBrasilia() {
		assertThat(LembreteTemplate.formatarPrazo(Fixtures.DUE)).isEqualTo("sábado, 21 de novembro de 2026 às 12:30 (horário de Brasília)");
	}

	@Test
	void conteudoTemNomePrazoELink() {
		var email = LembreteTemplate.montar(Fixtures.solicitacao("2:1:4"));
		assertThat(email.assunto()).isEqualTo("Lembrete: a tarefa \"T1 - Casos de uso\" vence em breve");
		assertThat(email.texto()).contains("Ana Souza", "T1 - Casos de uso", "sábado, 21 de novembro de 2026 às 12:30",
				"http://localhost:8080/mod/assign/view.php?id=2");
		assertThat(email.html()).contains("href=\"http://localhost:8080/mod/assign/view.php?id=2\"");
	}

	@Test
	void escapaHtml() {
		var s = Fixtures.solicitacao("k");
		var xss = new SolicitacaoLembrete(s.idempotencyKey(), s.courseId(), s.courseName(), s.assignId(), s.cmid(), "<script>x</script>",
				s.duedate(), s.duedateEpoch(), s.userId(), s.userFullname(), s.userEmail(), s.assignUrl(), s.createdAt());
		assertThat(LembreteTemplate.montar(xss).html()).contains("&lt;script&gt;").doesNotContain("<script>");
	}

}
