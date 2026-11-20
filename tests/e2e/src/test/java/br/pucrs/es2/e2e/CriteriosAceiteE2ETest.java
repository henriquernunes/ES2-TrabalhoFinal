package br.pucrs.es2.e2e;

import static br.pucrs.es2.e2e.support.Aguardar.ate;
import static org.assertj.core.api.Assertions.assertThat;

import br.pucrs.es2.e2e.support.Aguardar;
import br.pucrs.es2.e2e.support.Env;
import br.pucrs.es2.e2e.support.Evidencias;
import br.pucrs.es2.e2e.support.Http;
import br.pucrs.es2.e2e.support.Mailpit;
import br.pucrs.es2.e2e.support.Mailpit.Email;
import br.pucrs.es2.e2e.support.Shell;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import tools.jackson.databind.JsonNode;

// os 4 critérios de aceite rodando contra o compose, tudo pelo gateway
// evidências vão pra tests/e2e/evidencias
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CriteriosAceiteE2ETest {

	private static final List<String> ALUNOS = List.of("aluno1", "aluno2", "aluno3", "aluno4", "aluno5").stream()
			.map(a -> a + "@example.com").toList();
	private static final String ALUNO_QUE_ENVIOU_T2 = "aluno1@example.com";
	// t1 pros 5 + t2 pros 4 (aluno1 já enviou)
	private static final int ESPERADOS = 9;

	private final String acompanhamento = Env.gateway() + "/acompanhamento";
	private final String notificacoes = Env.gateway() + "/notificacoes";
	private JsonNode cicloInicial;

	@BeforeAll
	void subirAmbiente() {
		Shell.upAndSeed();
		aguardarRotas();
	}

	// o gateway só acha o serviço depois que ele aparece no eureka
	private void aguardarRotas() {
		ate("rotas do gateway", () -> List.of(Http.get(acompanhamento + "/status").status(), Http.get(notificacoes + "/notificacoes").status()),
				codigos -> codigos.equals(List.of(200, 200)), Duration.ofSeconds(90));
	}

	// o reset recria o container, então muda o ip
	private void resetNotificacoes() {
		Shell.resetNotificacoes();
		aguardarRotas();
	}

	@AfterAll
	void restaurar() {
		// volta a config do .env e garante que o mailpit ficou de pé
		Shell.compose("up -d --wait mailpit");
		Shell.compose("up -d --wait --no-deps --force-recreate notificacoes");
	}

	private JsonNode run() {
		var res = Http.post(acompanhamento + "/run");
		assertThat(res.status()).isEqualTo(200);
		return res.body();
	}

	private List<JsonNode> listarNotificacoes(String status) {
		var res = Http.get(notificacoes + "/notificacoes" + (status == null ? "" : "?status=" + status));
		assertThat(res.status()).isEqualTo(200);
		return StreamSupport.stream(res.body().spliterator(), false).toList();
	}

	private List<Email> aguardarEmails(int quantidade, Duration timeout) {
		return ate(quantidade + " e-mails no Mailpit", Mailpit::mensagens, m -> m.size() >= quantidade, timeout);
	}

	@Test
	@Order(1)
	@DisplayName("Critério 1: T1 (vence em ~12 h, ninguém entregou) -> todos os alunos recebem exatamente 1 lembrete")
	void criterio1() {
		resetNotificacoes();
		cicloInicial = run();
		assertThat(cicloInicial.path("tarefas_analisadas").asInt()).isEqualTo(5);
		assertThat(cicloInicial.path("tarefas_na_janela").asInt()).isEqualTo(2);
		assertThat(cicloInicial.path("alunos_analisados").asInt()).isEqualTo(5);
		assertThat(cicloInicial.path("solicitacoes_publicadas").asInt()).isEqualTo(ESPERADOS);
		assertThat(cicloInicial.path("erros")).isEmpty();

		aguardarEmails(ESPERADOS, Duration.ofSeconds(60));
		Aguardar.dormir(Duration.ofSeconds(3)); // espera pra ver se chega algum email a mais
		var mensagens = Mailpit.mensagens();
		for (String aluno : ALUNOS) {
			assertThat(Mailpit.para(mensagens, aluno, "T1")).as("T1 para %s", aluno).hasSize(1);
		}

		String texto = Mailpit.detalhe(Mailpit.para(mensagens, ALUNOS.get(1), "T1").getFirst().id()).path("Text").asString();
		assertThat(texto).contains("Bruno Lima")
				.containsPattern("vence em \\S+, \\d{1,2} de \\S+ de 20\\d\\d às \\d\\d:\\d\\d \\(horário de Brasília\\)")
				.containsPattern("http://localhost:8080/mod/assign/view\\.php\\?id=\\d+");
		Evidencias.salvar("criterio-1-2-ciclo.json", cicloInicial);
		Evidencias.salvar("criterio-1-exemplo-email.txt", texto);
	}

	@Test
	@Order(2)
	@DisplayName("Critério 2: T2 não vai para quem já enviou; T3, T4 e T5 não geram lembrete")
	void criterio2() {
		var mensagens = Mailpit.mensagens();
		assertThat(Mailpit.para(mensagens, ALUNO_QUE_ENVIOU_T2, "T2")).isEmpty();
		ALUNOS.stream().filter(a -> !a.equals(ALUNO_QUE_ENVIOU_T2))
				.forEach(a -> assertThat(Mailpit.para(mensagens, a, "T2")).as("T2 para %s", a).hasSize(1));
		for (String tarefa : List.of("T3", "T4", "T5")) {
			assertThat(mensagens).noneMatch(m -> m.assunto().contains("\"" + tarefa + " -"));
		}
		assertThat(mensagens).hasSize(ESPERADOS);

		Evidencias.salvar("criterio-1-2-mailpit.json", mensagens.stream()
				.sorted(Comparator.comparing(Email::assunto).thenComparing(m -> String.join(",", m.para())))
				.map(m -> java.util.Map.of("para", String.join(", ", m.para()), "assunto", m.assunto()))
				.toList());
		Evidencias.salvar("criterio-1-2-notificacoes.json", listarNotificacoes(null));
	}

	@Test
	@Order(3)
	@DisplayName("Critério 3: republicar (POST /reprocessar e novo POST /run) mantém 1 e-mail")
	void criterio3() {
		String inicio = Instant.now().toString();
		int antes = Mailpit.mensagens().size();
		JsonNode registroAntes = listarNotificacoes("ENVIADO").stream()
				.filter(n -> n.path("user_email").asString().equals(ALUNO_QUE_ENVIOU_T2) && n.path("assign_name").asString().startsWith("T1"))
				.findFirst().orElseThrow();
		String chave = registroAntes.path("idempotency_key").asString();

		var reprocessar = Http.post(notificacoes + "/notificacoes/" + chave + "/reprocessar");
		assertThat(reprocessar.status()).isEqualTo(202);
		assertThat(reprocessar.body().path("status_atual").asString()).isEqualTo("ENVIADO");
		var ciclo = run();
		assertThat(ciclo.path("solicitacoes_publicadas").asInt()).isEqualTo(ESPERADOS);

		// 1 do reprocessar + 9 do novo ciclo, todas têm que ser ignoradas
		var duplicadas = ate("logs de duplicada ignorada",
				() -> Shell.logs("notificacoes", inicio).stream().filter(l -> l.path("message").asString().equals("duplicada ignorada")).toList(),
				l -> l.size() >= ESPERADOS + 1);
		assertThat(duplicadas).filteredOn(l -> l.path("idempotency_key").asString().equals(chave)).hasSize(2);

		var depois = Mailpit.mensagens();
		assertThat(depois).hasSize(antes);
		assertThat(Mailpit.para(depois, ALUNO_QUE_ENVIOU_T2, "T1")).hasSize(1);
		JsonNode registro = listarNotificacoes(null).stream().filter(n -> n.path("idempotency_key").asString().equals(chave)).findFirst().orElseThrow();
		assertThat(registro.path("status").asString()).isEqualTo("ENVIADO");
		assertThat(registro.path("tentativas").asInt()).isEqualTo(1);

		var evidencia = new LinkedHashMap<String, Object>();
		evidencia.put("reprocessar", reprocessar.body());
		evidencia.put("ciclo", ciclo);
		evidencia.put("registro", registro);
		evidencia.put("emails_antes", antes);
		evidencia.put("emails_depois", depois.size());
		Evidencias.salvar("criterio-3-reprocessar.json", evidencia);
		Evidencias.salvar("criterio-3-logs.log", Evidencias.jsonLines(duplicadas));
	}

	@Test
	@Order(4)
	@DisplayName("Critério 4: Mailpit parado -> falha no banco/log -> Mailpit volta -> e-mails chegam após retry")
	void criterio4() {
		resetNotificacoes();
		String inicio = Instant.now().toString();
		Shell.compose("stop mailpit");

		var ciclo = run();
		assertThat(ciclo.path("solicitacoes_publicadas").asInt()).isEqualTo(ESPERADOS);

		// a falha tem que aparecer no banco e no log
		var comFalha = ate("falha registrada no banco", () -> listarNotificacoes("PENDENTE"),
				rows -> rows.stream().anyMatch(n -> n.path("tentativas").asInt() >= 1 && !n.path("ultimo_erro").isNull()));
		ate("log de nova tentativa",
				() -> Shell.logs("notificacoes", inicio).stream().filter(l -> l.path("message").asString().equals("nova tentativa agendada")).toList(),
				l -> !l.isEmpty());
		Evidencias.salvar("criterio-4-falha-notificacoes.json", comFalha);

		Shell.compose("start mailpit");
		aguardarEmails(ESPERADOS, Duration.ofSeconds(180));
		var enviados = ate(ESPERADOS + " registros ENVIADO", () -> listarNotificacoes("ENVIADO"), rows -> rows.size() == ESPERADOS,
				Duration.ofSeconds(180));

		assertThat(enviados).allMatch(n -> n.path("ultimo_erro").isNull());
		assertThat(enviados).anyMatch(n -> n.path("tentativas").asInt() >= 2);
		assertThat(Mailpit.mensagens()).hasSize(ESPERADOS);
		assertThat(listarNotificacoes("FALHOU")).isEmpty();

		var mensagensDeLog = Set.of("falha ao enviar e-mail", "nova tentativa agendada", "e-mail enviado");
		var logs = Shell.logs("notificacoes", inicio).stream().filter(l -> mensagensDeLog.contains(l.path("message").asString())).toList();
		Evidencias.salvar("criterio-4-depois-notificacoes.json", enviados);
		Evidencias.salvar("criterio-4-logs.log", Evidencias.jsonLines(logs));
	}

}
