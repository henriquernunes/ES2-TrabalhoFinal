package br.pucrs.es2.notificacoes.notificacao;

import br.pucrs.es2.notificacoes.mail.MailService;
import br.pucrs.es2.notificacoes.messaging.Filas;
import br.pucrs.es2.notificacoes.messaging.RabbitPublisher;
import br.pucrs.es2.notificacoes.notificacao.dto.NotificacaoResponse;
import br.pucrs.es2.notificacoes.notificacao.dto.ReprocessarResponse;
import br.pucrs.es2.notificacoes.notificacao.dto.SolicitacaoLembrete;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificacoesService {

	private static final Logger log = LoggerFactory.getLogger(NotificacoesService.class);

	public enum Resultado {
		ENVIADO, DUPLICADA
	}

	public static class FalhaEnvioException extends RuntimeException {
		public FalhaEnvioException(String message, Throwable cause) {
			super(message, cause);
		}
	}

	private final NotificacaoRepository repository;
	private final MailService mailService;
	private final RabbitPublisher publisher;
	private final Clock clock;

	public NotificacoesService(NotificacaoRepository repository, MailService mailService, RabbitPublisher publisher, Clock clock) {
		this.repository = repository;
		this.mailService = mailService;
		this.publisher = publisher;
		this.clock = clock;
	}

	// se já foi ENVIADO não manda de novo. se o smtp falhar salva o erro e lança FalhaEnvioException
	public Resultado processar(SolicitacaoLembrete s, String payload) {
		var agora = clock.instant();
		var notificacao = repository.findByIdempotencyKey(s.idempotencyKey()).orElse(null);
		if (notificacao != null && notificacao.isEnviado()) {
			log.atInfo().addKeyValue("idempotency_key", s.idempotencyKey()).log("duplicada ignorada");
			return Resultado.DUPLICADA;
		}
		if (notificacao == null) {
			notificacao = new Notificacao(s.idempotencyKey(), s.userEmail(), s.assignName(), payload, agora);
		} else if (notificacao.getStatus() == StatusNotificacao.FALHOU) {
			log.atInfo().addKeyValue("idempotency_key", s.idempotencyKey()).log("registro FALHOU reaberto por nova solicitação");
			notificacao.reabrir(payload, agora);
		}
		notificacao.registrarTentativa(agora);
		notificacao = repository.save(notificacao);

		try {
			mailService.enviarLembrete(s);
		} catch (RuntimeException e) {
			String erro = e.getClass().getSimpleName() + ": " + e.getMessage();
			notificacao.registrarErro(erro, clock.instant());
			repository.save(notificacao);
			log.atWarn().addKeyValue("idempotency_key", s.idempotencyKey()).addKeyValue("tentativas", notificacao.getTentativas())
					.addKeyValue("err", erro).log("falha ao enviar e-mail");
			throw new FalhaEnvioException(erro, e);
		}
		notificacao.marcarEnviado(clock.instant());
		repository.save(notificacao);
		log.atInfo().addKeyValue("idempotency_key", s.idempotencyKey()).addKeyValue("tentativas", notificacao.getTentativas())
				.addKeyValue("user_email", s.userEmail()).log("e-mail enviado");
		return Resultado.ENVIADO;
	}

	public void marcarFalhou(String key, String erro) {
		repository.findByIdempotencyKey(key).ifPresent(n -> {
			n.marcarFalhou(erro, clock.instant());
			repository.save(n);
		});
		log.atError().addKeyValue("idempotency_key", key).addKeyValue("err", erro).log("tentativas esgotadas; mensagem enviada para a DLQ");
	}

	public List<NotificacaoResponse> listar(StatusNotificacao status) {
		var registros = status == null ? repository.findAllByOrderByCriadoEmDescIdDesc() : repository.findByStatusOrderByCriadoEmDescIdDesc(status);
		return registros.stream().map(NotificacaoResponse::of).toList();
	}

	// sempre republica, mesmo se já ENVIADO (aí o consumidor ignora, é o teste de idempotência)
	public ReprocessarResponse reprocessar(String key) {
		var notificacao = repository.findByIdempotencyKey(key).orElseThrow(() -> new NotificacaoNaoEncontradaException(key));
		publisher.publicar(Filas.SOLICITACOES, notificacao.getPayload().getBytes(StandardCharsets.UTF_8), Map.of(), key);
		log.atInfo().addKeyValue("idempotency_key", key).addKeyValue("status", notificacao.getStatus()).log("solicitação republicada para reprocessamento");
		String mensagem = notificacao.isEnviado()
				? "republicada; já ENVIADO, o consumidor vai ignorar (idempotência)"
				: "republicada na fila principal";
		return new ReprocessarResponse(key, notificacao.getStatus(), mensagem);
	}

}
