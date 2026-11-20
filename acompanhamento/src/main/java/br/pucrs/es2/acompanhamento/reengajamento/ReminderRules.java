package br.pucrs.es2.acompanhamento.reengajamento;

import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Assignment;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Course;
import br.pucrs.es2.acompanhamento.moodle.dto.Student;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ReminderRules {

	private ReminderRules() {
	}

	public static String idempotencyKey(long courseId, long assignId, long userId) {
		return courseId + ":" + assignId + ":" + userId;
	}

	// agora < prazo <= agora + janela (0 = sem prazo)
	public static boolean isDueWithinWindow(long duedate, Instant now, Duration window) {
		long nowEpoch = now.getEpochSecond();
		return duedate > 0 && duedate > nowEpoch && duedate <= nowEpoch + window.toSeconds();
	}

	public static List<Assignment> assignmentsInWindow(List<Assignment> assignments, Instant now, Duration window) {
		return assignments.stream().filter(a -> isDueWithinWindow(a.duedate(), now, window)).toList();
	}

	public static String assignUrl(String moodlePublicUrl, long cmid) {
		return moodlePublicUrl.replaceAll("/+$", "") + "/mod/assign/view.php?id=" + cmid;
	}

	// submittedByAssign: assign_id -> alunos que já enviaram
	public static List<SolicitacaoLembrete> buildSolicitacoes(Course course, List<Assignment> assignments,
			List<Student> students, Map<Long, Set<Long>> submittedByAssign, String moodlePublicUrl, Instant now) {
		String createdAt = now.toString();
		return assignments.stream()
				.flatMap(assignment -> {
					Set<Long> submitted = submittedByAssign.getOrDefault(assignment.id(), Set.of());
					return students.stream()
							.filter(student -> !submitted.contains(student.id()))
							.map(student -> new SolicitacaoLembrete(
									idempotencyKey(course.id(), assignment.id(), student.id()),
									course.id(),
									course.fullname(),
									assignment.id(),
									assignment.cmid(),
									assignment.name(),
									Instant.ofEpochSecond(assignment.duedate()).toString(),
									assignment.duedate(),
									student.id(),
									student.fullname(),
									student.email(),
									assignUrl(moodlePublicUrl, assignment.cmid()),
									createdAt));
				})
				.toList();
	}

}
