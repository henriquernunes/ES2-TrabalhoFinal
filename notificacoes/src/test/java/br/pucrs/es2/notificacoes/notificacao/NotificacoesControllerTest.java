package br.pucrs.es2.notificacoes.notificacao;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.pucrs.es2.notificacoes.notificacao.dto.NotificacaoResponse;
import br.pucrs.es2.notificacoes.notificacao.dto.ReprocessarResponse;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(NotificacoesController.class)
class NotificacoesControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private NotificacoesService service;

	@Test
	void listaFiltrandoPorStatus() throws Exception {
		when(service.listar(StatusNotificacao.ENVIADO)).thenReturn(List.of(new NotificacaoResponse("2:1:4", StatusNotificacao.ENVIADO, 1, null,
				"aluno1@example.com", "T1", Instant.EPOCH, Instant.EPOCH, Instant.EPOCH)));
		mvc.perform(get("/notificacoes").param("status", "ENVIADO"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].idempotency_key").value("2:1:4"))
				.andExpect(jsonPath("$[0].status").value("ENVIADO"));
	}

	@Test
	void statusInvalidoDa400() throws Exception {
		mvc.perform(get("/notificacoes").param("status", "QUALQUER")).andExpect(status().isBadRequest());
	}

	@Test
	void reprocessarDa202() throws Exception {
		when(service.reprocessar("2:1:4")).thenReturn(new ReprocessarResponse("2:1:4", StatusNotificacao.ENVIADO, "ok"));
		mvc.perform(post("/notificacoes/2:1:4/reprocessar"))
				.andExpect(status().isAccepted())
				.andExpect(jsonPath("$.status_atual").value("ENVIADO"));
	}

	@Test
	void reprocessarChaveInexistenteDa404() throws Exception {
		when(service.reprocessar("x")).thenThrow(new NotificacaoNaoEncontradaException("x"));
		mvc.perform(post("/notificacoes/x/reprocessar")).andExpect(status().isNotFound());
	}

}
