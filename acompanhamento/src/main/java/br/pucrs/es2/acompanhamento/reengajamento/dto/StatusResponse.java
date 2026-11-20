package br.pucrs.es2.acompanhamento.reengajamento.dto;

public record StatusResponse(
		boolean emExecucao,
		long courseId,
		long intervaloSegundos,
		long janelaHoras,
		CicloResultado ultimoCiclo) {
}
