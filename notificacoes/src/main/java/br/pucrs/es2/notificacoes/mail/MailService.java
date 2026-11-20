package br.pucrs.es2.notificacoes.mail;

import br.pucrs.es2.notificacoes.config.NotificacoesProperties;
import br.pucrs.es2.notificacoes.notificacao.dto.SolicitacaoLembrete;
import jakarta.mail.MessagingException;
import java.nio.charset.StandardCharsets;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

// os timeouts ficam no spring.mail.properties
@Service
public class MailService {

	public static final String HEADER_IDEMPOTENCY_KEY = "X-Idempotency-Key";

	private final JavaMailSender mailSender;
	private final String from;

	public MailService(JavaMailSender mailSender, NotificacoesProperties props) {
		this.mailSender = mailSender;
		this.from = props.smtpFrom();
	}

	public void enviarLembrete(SolicitacaoLembrete solicitacao) {
		var email = LembreteTemplate.montar(solicitacao);
		var message = mailSender.createMimeMessage();
		try {
			var helper = new MimeMessageHelper(message, true, StandardCharsets.UTF_8.name());
			helper.setFrom(from);
			helper.setTo(solicitacao.userEmail());
			helper.setSubject(email.assunto());
			helper.setText(email.texto(), email.html());
			message.setHeader(HEADER_IDEMPOTENCY_KEY, solicitacao.idempotencyKey());
		} catch (MessagingException e) {
			throw new MailSendException("falha ao montar o e-mail: " + e.getMessage(), e);
		}
		mailSender.send(message);
	}

}
