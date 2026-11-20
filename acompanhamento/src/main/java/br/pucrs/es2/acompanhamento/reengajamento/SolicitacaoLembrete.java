package br.pucrs.es2.acompanhamento.reengajamento;

// mensagem da fila reengajamento.solicitacoes (contrato no docs/ARQUITETURA.md)
// idempotency_key = course_id:assign_id:user_id, datas em utc
public record SolicitacaoLembrete(
		String idempotencyKey,
		long courseId,
		String courseName,
		long assignId,
		long cmid,
		String assignName,
		String duedate,
		long duedateEpoch,
		long userId,
		String userFullname,
		String userEmail,
		String assignUrl,
		String createdAt) {
}
