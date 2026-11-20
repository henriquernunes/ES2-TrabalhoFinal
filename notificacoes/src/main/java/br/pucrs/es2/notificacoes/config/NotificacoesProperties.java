package br.pucrs.es2.notificacoes.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

// retryBaseDelay dobra a cada tentativa
@Validated
@ConfigurationProperties(prefix = "notificacoes")
public record NotificacoesProperties(
		@NotBlank String smtpFrom,
		@Min(0) int maxRetries,
		@NotNull Duration retryBaseDelay) {
}
