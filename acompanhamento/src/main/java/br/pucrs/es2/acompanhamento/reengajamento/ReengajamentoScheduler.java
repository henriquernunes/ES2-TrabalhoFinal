package br.pucrs.es2.acompanhamento.reengajamento;

import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReengajamentoScheduler {

	private static final Logger log = LoggerFactory.getLogger(ReengajamentoScheduler.class);

	private final ReengajamentoService service;

	public ReengajamentoScheduler(ReengajamentoService service) {
		this.service = service;
	}

	@Scheduled(fixedRateString = "${reengajamento.poll-interval-seconds}",
			initialDelayString = "${reengajamento.poll-interval-seconds}",
			timeUnit = TimeUnit.SECONDS)
	public void tick() {
		if (service.isRunning()) {
			log.warn("ciclo anterior ainda em execução; pulando este disparo");
			return;
		}
		service.runCycle("agendado");
	}

}
