package br.pucrs.es2.acompanhamento.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "reengajamento")
public record AcompanhamentoProperties(
		@Positive long courseId,
		@Min(5) long pollIntervalSeconds,
		@Positive long reminderWindowHours,
		@Valid @NotNull Moodle moodle) {

	public record Moodle(
			@NotBlank String url,
			@NotBlank String publicUrl,
			// pode vir vazio antes do seed
			String token,
			@NotNull Duration timeout) {
	}

}
