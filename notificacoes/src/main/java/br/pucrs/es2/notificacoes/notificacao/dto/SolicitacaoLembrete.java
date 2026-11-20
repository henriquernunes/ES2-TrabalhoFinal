package br.pucrs.es2.notificacoes.notificacao.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

// campo a mais é ignorado, se não validar vai direto pra dlq
public record SolicitacaoLembrete(
		@NotBlank String idempotencyKey,
		@NotNull @Positive Long courseId,
		@NotBlank String courseName,
		@NotNull @Positive Long assignId,
		@NotNull @Positive Long cmid,
		@NotBlank String assignName,
		@NotBlank String duedate,
		@NotNull @Positive Long duedateEpoch,
		@NotNull @Positive Long userId,
		@NotBlank String userFullname,
		@NotBlank @Email String userEmail,
		@NotBlank String assignUrl,
		String createdAt) {
}
