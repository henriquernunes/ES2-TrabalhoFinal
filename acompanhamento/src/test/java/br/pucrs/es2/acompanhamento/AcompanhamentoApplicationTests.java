package br.pucrs.es2.acompanhamento;

import static org.assertj.core.api.Assertions.assertThat;

import br.pucrs.es2.acompanhamento.reengajamento.SolicitacaoLembrete;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.json.JsonMapper;

@SpringBootTest
class AcompanhamentoApplicationTests {

	@Autowired
	private JsonMapper jsonMapper;

	@Test
	void mensagemUsaOContratoEmSnakeCase() {
		var json = jsonMapper.writeValueAsString(new SolicitacaoLembrete("2:1:4", 2, "ES2", 1, 2, "T1",
				"2026-11-21T07:00:00Z", 1795244400, 4, "Ana", "aluno1@example.com", "http://x/mod/assign/view.php?id=2", "2026-11-20T00:00:00Z"));
		assertThat(json).contains("\"idempotency_key\":\"2:1:4\"", "\"duedate_epoch\":1795244400", "\"user_email\":\"aluno1@example.com\"",
				"\"assign_url\":", "\"created_at\":", "\"course_name\":", "\"user_fullname\":", "\"assign_name\":", "\"cmid\":2");
	}

}
