package br.pucrs.es2.notificacoes.mail;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucrs.es2.notificacoes.Fixtures;
import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.time.Duration;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;

class MailServiceTest {

	@Test
	void enviaComRemetenteDestinatarioAssuntoECabecalhoDeIdempotencia() throws Exception {
		var sender = mock(JavaMailSender.class);
		when(sender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));
		var service = new MailService(sender, new NotificacoesProperties("Moodle Reengajamento <nao-responda@example.com>", 5, Duration.ofSeconds(5)));

		service.enviarLembrete(Fixtures.solicitacao("2:1:4"));

		var captor = ArgumentCaptor.forClass(MimeMessage.class);
		verify(sender).send(captor.capture());
		var msg = captor.getValue();
		assertThat(msg.getFrom()[0].toString()).isEqualTo("Moodle Reengajamento <nao-responda@example.com>");
		assertThat(msg.getAllRecipients()[0].toString()).isEqualTo("aluno1@example.com");
		assertThat(msg.getSubject()).isEqualTo("Lembrete: a tarefa \"T1 - Casos de uso\" vence em breve");
		assertThat(msg.getHeader(MailService.HEADER_IDEMPOTENCY_KEY)).containsExactly("2:1:4");
	}

}
