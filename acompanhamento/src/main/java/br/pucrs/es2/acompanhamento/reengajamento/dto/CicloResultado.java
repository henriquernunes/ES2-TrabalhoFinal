package br.pucrs.es2.acompanhamento.reengajamento.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record CicloResultado(
		@Schema(allowableValues = { "agendado", "manual" }) String trigger,
		@Schema(example = "2026-11-20T18:00:00Z") String startedAt,
		@Schema(example = "2026-11-20T18:00:01.234Z") String finishedAt,
		@Schema(description = "Tarefas retornadas pelo Moodle para a disciplina") int tarefasAnalisadas,
		@Schema(description = "Tarefas com prazo dentro da janela") int tarefasNaJanela,
		@Schema(description = "Estudantes matriculados considerados") int alunosAnalisados,
		int solicitacoesPublicadas,
		List<String> erros) {
}
