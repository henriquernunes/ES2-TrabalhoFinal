package br.pucrs.es2.acompanhamento.moodle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Assignment;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.AssignmentSubmissions;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.AssignmentsResponse;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Course;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.EnrolledUser;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Role;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Submission;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.SubmissionsResponse;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Warning;
import br.pucrs.es2.acompanhamento.moodle.dto.Student;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MoodleServiceTest {

	private final MoodleClient client = mock(MoodleClient.class);
	private final MoodleService service = new MoodleService(client);

	private static List<Role> role(String shortname) {
		return List.of(new Role(1, shortname, shortname));
	}

	@Test
	void getCourseAssignmentsDevolveADisciplinaPedida() {
		var t1 = new Assignment(1, 2, 2, "T1", 10);
		when(client.call(eq("mod_assign_get_assignments"), anyMap(), eq(AssignmentsResponse.class)))
				.thenReturn(new AssignmentsResponse(List.of(new Course(2, "ES2", List.of(t1))), List.of()));

		assertThat(service.getCourseAssignments(2)).isEqualTo(new Course(2, "ES2", List.of(t1)));
		verify(client).call("mod_assign_get_assignments", Map.of("courseids", List.of(2L), "includenotenrolledcourses", true), AssignmentsResponse.class);
	}

	@Test
	void getCourseAssignmentsFalhaQuandoODisciplinaNaoVolta() {
		when(client.call(eq("mod_assign_get_assignments"), anyMap(), eq(AssignmentsResponse.class)))
				.thenReturn(new AssignmentsResponse(List.of(), List.of(new Warning("course", 2L, "1", "No access rights"))));
		assertThatThrownBy(() -> service.getCourseAssignments(2)).isInstanceOf(MoodleApiException.class).hasMessageContaining("No access rights");
	}

	@Test
	void getEnrolledStudentsMantemSoEstudantesComEmail() {
		when(client.call(eq("core_enrol_get_enrolled_users"), anyMap(), eq(EnrolledUser[].class))).thenReturn(new EnrolledUser[] {
				new EnrolledUser(4, "Ana", "ana@example.com", role("student")),
				new EnrolledUser(5, "Prof", "prof@example.com", role("editingteacher")),
				new EnrolledUser(6, "Sem e-mail", null, role("student")),
				new EnrolledUser(7, "Sem papel", "x@example.com", null) });

		assertThat(service.getEnrolledStudents(2)).containsExactly(new Student(4, "Ana", "ana@example.com"));
	}

	@Test
	void getSubmittedUserIdsConsideraSoSubmitted() {
		when(client.call(eq("mod_assign_get_submissions"), anyMap(), eq(SubmissionsResponse.class))).thenReturn(new SubmissionsResponse(
				List.of(new AssignmentSubmissions(2, List.of(
						new Submission(4, "submitted"), new Submission(5, "draft"), new Submission(6, "new")))),
				List.of()));

		var result = service.getSubmittedUserIds(List.of(1L, 2L));
		assertThat(result.get(1L)).isEmpty();
		assertThat(result.get(2L)).containsExactly(4L);
	}

	@Test
	void getSubmittedUserIdsNaoChamaOMoodleSemTarefas() {
		assertThat(service.getSubmittedUserIds(List.of())).isEmpty();
		verifyNoInteractions(client);
	}

}
