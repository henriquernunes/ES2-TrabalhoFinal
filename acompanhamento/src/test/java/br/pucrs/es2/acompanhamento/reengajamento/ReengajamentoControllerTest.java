package br.pucrs.es2.acompanhamento.reengajamento;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.pucrs.es2.acompanhamento.reengajamento.dto.CicloResultado;
import br.pucrs.es2.acompanhamento.reengajamento.dto.StatusResponse;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ReengajamentoController.class)
class ReengajamentoControllerTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private ReengajamentoService service;

	@Test
	void runDevolveOResultadoDoCicloEmSnakeCase() throws Exception {
		when(service.runCycle("manual")).thenReturn(new CicloResultado("manual", "a", "b", 5, 2, 5, 9, List.of()));
		mvc.perform(post("/run"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.trigger").value("manual"))
				.andExpect(jsonPath("$.solicitacoes_publicadas").value(9))
				.andExpect(jsonPath("$.tarefas_na_janela").value(2));
	}

	@Test
	void runResponde409ComCicloEmAndamento() throws Exception {
		when(service.runCycle("manual")).thenThrow(new CicloEmAndamentoException());
		mvc.perform(post("/run")).andExpect(status().isConflict());
	}

	@Test
	void statusDevolveOStatus() throws Exception {
		when(service.status()).thenReturn(new StatusResponse(false, 2, 3600, 24, null));
		mvc.perform(get("/status"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.em_execucao").value(false))
				.andExpect(jsonPath("$.intervalo_segundos").value(3600));
	}

}
