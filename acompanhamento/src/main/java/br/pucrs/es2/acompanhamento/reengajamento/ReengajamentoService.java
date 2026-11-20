package br.pucrs.es2.acompanhamento.reengajamento;

import br.pucrs.es2.acompanhamento.config.AcompanhamentoProperties;
import br.pucrs.es2.acompanhamento.messaging.SolicitacaoPublisher;
import br.pucrs.es2.acompanhamento.moodle.MoodleApiException;
import br.pucrs.es2.acompanhamento.moodle.MoodleService;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Assignment;
import br.pucrs.es2.acompanhamento.reengajamento.dto.CicloResultado;
import br.pucrs.es2.acompanhamento.reengajamento.dto.StatusResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ReengajamentoService {

	private static final Logger log = LoggerFactory.getLogger(ReengajamentoService.class);

	private final MoodleService moodle;
	private final SolicitacaoPublisher publisher;
	private final AcompanhamentoProperties props;
	private final Clock clock;
	private final AtomicBoolean running = new AtomicBoolean(false);
	private volatile CicloResultado ultimoCiclo;

	public ReengajamentoService(MoodleService moodle, SolicitacaoPublisher publisher, AcompanhamentoProperties props, Clock clock) {
		this.moodle = moodle;
		this.publisher = publisher;
		this.props = props;
		this.clock = clock;
	}

	public boolean isRunning() {
		return running.get();
	}

	public StatusResponse status() {
		return new StatusResponse(running.get(), props.courseId(), props.pollIntervalSeconds(), props.reminderWindowHours(), ultimoCiclo);
	}

	// erro do moodle/rabbit não sobe exceção, vai pro resultado e pro log
	public CicloResultado runCycle(String trigger) {
		if (!running.compareAndSet(false, true)) {
			throw new CicloEmAndamentoException();
		}
		Instant now = clock.instant();
		var ciclo = new Contadores();
		log.atInfo().addKeyValue("trigger", trigger).log("ciclo iniciado");
		try {
			execute(ciclo, now);
		} catch (MoodleApiException e) {
			ciclo.erros.add(e.getWsfunction() + ": " + e.getMessage());
			log.atError().addKeyValue("wsfunction", e.getWsfunction()).addKeyValue("err", e.getMessage()).log("ciclo interrompido por falha do Moodle");
		} catch (RuntimeException e) {
			ciclo.erros.add(e.getMessage());
			log.atError().addKeyValue("err", e.getMessage()).log("ciclo interrompido por falha");
		} finally {
			ultimoCiclo = ciclo.toResultado(trigger, now, clock.instant());
			running.set(false);
		}
		log.atInfo()
				.addKeyValue("trigger", trigger)
				.addKeyValue("tarefas_analisadas", ultimoCiclo.tarefasAnalisadas())
				.addKeyValue("tarefas_na_janela", ultimoCiclo.tarefasNaJanela())
				.addKeyValue("alunos_analisados", ultimoCiclo.alunosAnalisados())
				.addKeyValue("solicitacoes_publicadas", ultimoCiclo.solicitacoesPublicadas())
				.addKeyValue("erros", ultimoCiclo.erros().size())
				.log("ciclo concluído");
		return ultimoCiclo;
	}

	private void execute(Contadores ciclo, Instant now) {
		var course = moodle.getCourseAssignments(props.courseId());
		List<Assignment> inWindow = ReminderRules.assignmentsInWindow(course.assignments(), now, Duration.ofHours(props.reminderWindowHours()));
		ciclo.tarefasAnalisadas = course.assignments().size();
		ciclo.tarefasNaJanela = inWindow.size();
		if (inWindow.isEmpty()) {
			return;
		}

		var students = moodle.getEnrolledStudents(props.courseId());
		var submitted = moodle.getSubmittedUserIds(inWindow.stream().map(Assignment::id).toList());
		ciclo.alunosAnalisados = students.size();

		var solicitacoes = ReminderRules.buildSolicitacoes(course, inWindow, students, submitted, props.moodle().publicUrl(), now);
		for (var solicitacao : solicitacoes) {
			String key = solicitacao.idempotencyKey();
			try {
				publisher.publicar(solicitacao);
				ciclo.solicitacoesPublicadas++;
				log.atInfo().addKeyValue("idempotency_key", key).addKeyValue("assign_id", solicitacao.assignId()).log("solicitação publicada");
			} catch (RuntimeException e) {
				ciclo.erros.add(key + ": falha ao publicar (" + e.getMessage() + ")");
				log.atError().addKeyValue("idempotency_key", key).addKeyValue("err", e.getMessage()).log("falha ao publicar solicitação");
			}
		}
	}

	private static final class Contadores {
		int tarefasAnalisadas;
		int tarefasNaJanela;
		int alunosAnalisados;
		int solicitacoesPublicadas;
		final List<String> erros = new ArrayList<>();

		CicloResultado toResultado(String trigger, Instant start, Instant end) {
			return new CicloResultado(trigger, start.toString(), end.toString(), tarefasAnalisadas, tarefasNaJanela,
					alunosAnalisados, solicitacoesPublicadas, List.copyOf(erros));
		}
	}

}
