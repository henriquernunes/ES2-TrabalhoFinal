package br.pucrs.es2.acompanhamento.reengajamento;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

class ReengajamentoSchedulerTest {

	private final ReengajamentoService service = mock(ReengajamentoService.class);
	private final ReengajamentoScheduler scheduler = new ReengajamentoScheduler(service);

	@Test
	void intervaloVemDePollIntervalSeconds() throws NoSuchMethodException {
		Scheduled scheduled = ReengajamentoScheduler.class.getMethod("tick").getAnnotation(Scheduled.class);
		assertThat(scheduled.fixedRateString()).isEqualTo("${reengajamento.poll-interval-seconds}");
		assertThat(scheduled.timeUnit()).isEqualTo(TimeUnit.SECONDS);
	}

	@Test
	void disparaCicloAgendado() {
		scheduler.tick();
		verify(service).runCycle("agendado");
	}

	@Test
	void pulaODisparoSeOCicloAnteriorAindaRoda() {
		when(service.isRunning()).thenReturn(true);
		scheduler.tick();
		verify(service, never()).runCycle(anyString());
	}

}
