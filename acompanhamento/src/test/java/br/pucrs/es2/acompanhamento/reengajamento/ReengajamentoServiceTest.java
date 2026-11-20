package br.pucrs.es2.acompanhamento.reengajamento;

import static br.pucrs.es2.acompanhamento.Fixtures.ASSIGNMENTS;
import static br.pucrs.es2.acompanhamento.Fixtures.NOW;
import static br.pucrs.es2.acompanhamento.Fixtures.STUDENTS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.pucrs.es2.acompanhamento.Fixtures;
import br.pucrs.es2.acompanhamento.messaging.SolicitacaoPublisher;
import br.pucrs.es2.acompanhamento.moodle.MoodleApiException;
import br.pucrs.es2.acompanhamento.moodle.MoodleService;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Course;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ReengajamentoServiceTest {

	private final MoodleService moodle = mock(MoodleService.class);
	private final SolicitacaoPublisher publisher = mock(SolicitacaoPublisher.class);
	private final ReengajamentoService service = new ReengajamentoService(moodle, publisher, Fixtures.props(), Clock.fixed(NOW, ZoneOffset.UTC));

	@BeforeEach
	void setUp() {
		when(moodle.getCourseAssignments(2)).thenReturn(new Course(2, "ES2", ASSIGNMENTS));
		when(moodle.getEnrolledStudents(2)).thenReturn(STUDENTS);
		when(moodle.getSubmittedUserIds(anyList())).thenReturn(Map.of(2L, Set.of(4L)));
	}

	@Test
	void publicaUmaMensagemPorParPendenteDentroDaJanela() {
		var ciclo = service.runCycle("manual");

		verify(moodle).getSubmittedUserIds(List.of(1L, 2L));
		var captor = ArgumentCaptor.forClass(SolicitacaoLembrete.class);
		verify(publisher, times(5)).publicar(captor.capture());
		assertThat(captor.getAllValues()).extracting(SolicitacaoLembrete::idempotencyKey)
				.containsExactly("2:1:4", "2:1:5", "2:1:6", "2:2:5", "2:2:6");
		assertThat(ciclo.tarefasAnalisadas()).isEqualTo(5);
		assertThat(ciclo.tarefasNaJanela()).isEqualTo(2);
		assertThat(ciclo.alunosAnalisados()).isEqualTo(3);
		assertThat(ciclo.solicitacoesPublicadas()).isEqualTo(5);
		assertThat(ciclo.erros()).isEmpty();
		assertThat(service.status().ultimoCiclo()).isEqualTo(ciclo);
	}

	@Test
	void naoConsultaAlunosQuandoNenhumaTarefaEstaNaJanela() {
		when(moodle.getCourseAssignments(2)).thenReturn(new Course(2, "ES2", ASSIGNMENTS.subList(2, 5)));
		var ciclo = service.runCycle("agendado");
		verify(moodle, never()).getEnrolledStudents(anyLong());
		verify(publisher, never()).publicar(any());
		assertThat(ciclo.tarefasNaJanela()).isZero();
	}

	@Test
	void registraFalhaDoMoodleSemLancarELiberaOProximoCiclo() {
		when(moodle.getCourseAssignments(2)).thenThrow(new MoodleApiException("invalidtoken: Invalid token", "mod_assign_get_assignments", "invalidtoken"));
		var ciclo = service.runCycle("agendado");
		assertThat(ciclo.erros()).containsExactly("mod_assign_get_assignments: invalidtoken: Invalid token");
		verify(publisher, never()).publicar(any());
		assertThat(service.isRunning()).isFalse();
	}

	@Test
	void continuaPublicandoQuandoUmaPublicacaoFalha() {
		doThrow(new RuntimeException("timeout")).doNothing().when(publisher).publicar(any());
		var ciclo = service.runCycle("manual");
		assertThat(ciclo.solicitacoesPublicadas()).isEqualTo(4);
		assertThat(ciclo.erros()).containsExactly("2:1:4: falha ao publicar (timeout)");
	}

	@Test
	void impedeDoisCiclosSimultaneos() {
		var segundo = new AtomicReference<Throwable>();
		doAnswer(inv -> {
			try {
				service.runCycle("manual");
			} catch (Throwable t) {
				segundo.set(t);
			}
			return null;
		}).doNothing().when(publisher).publicar(any());

		service.runCycle("agendado");
		assertThat(segundo.get()).isInstanceOf(CicloEmAndamentoException.class);
		assertThatThrownBy(() -> {
			throw segundo.get();
		}).hasMessage("já existe um ciclo em execução");
	}

}
