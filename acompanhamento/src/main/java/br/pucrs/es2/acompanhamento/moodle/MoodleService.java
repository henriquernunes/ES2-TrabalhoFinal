package br.pucrs.es2.acompanhamento.moodle;

import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.AssignmentsResponse;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Course;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.EnrolledUser;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.SubmissionsResponse;
import br.pucrs.es2.acompanhamento.moodle.dto.MoodleDtos.Warning;
import br.pucrs.es2.acompanhamento.moodle.dto.Student;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class MoodleService {

	private static final String STUDENT_ROLE = "student";
	private static final String SUBMITTED = "submitted";

	private final MoodleClient client;

	public MoodleService(MoodleClient client) {
		this.client = client;
	}

	public Course getCourseAssignments(long courseId) {
		String fn = "mod_assign_get_assignments";
		var res = client.call(fn, Map.of(
				"courseids", List.of(courseId),
				// o usuário do ws não é matriculado na disciplina
				"includenotenrolledcourses", true), AssignmentsResponse.class);
		return nullSafe(res.courses()).stream()
				.filter(c -> c.id() == courseId)
				.findFirst()
				.map(c -> new Course(c.id(), c.fullname(), nullSafe(c.assignments())))
				.orElseThrow(() -> new MoodleApiException(
						"disciplina " + courseId + " não retornada" + describe(res.warnings()), fn, "nocourse"));
	}

	// só alunos (papel student) que têm email
	public List<Student> getEnrolledStudents(long courseId) {
		var users = client.call("core_enrol_get_enrolled_users", Map.of(
				"courseid", courseId,
				"options", List.of(
						Map.of("name", "onlyactive", "value", 1),
						Map.of("name", "userfields", "value", "id,fullname,email,roles"))),
				EnrolledUser[].class);
		return Arrays.stream(users)
				.filter(u -> nullSafe(u.roles()).stream().anyMatch(r -> STUDENT_ROLE.equals(r.shortname())))
				.filter(u -> u.email() != null && !u.email().isBlank())
				.map(u -> new Student(u.id(), u.fullname(), u.email()))
				.toList();
	}

	// assign_id -> alunos que já enviaram
	public Map<Long, Set<Long>> getSubmittedUserIds(List<Long> assignIds) {
		var result = new HashMap<Long, Set<Long>>();
		assignIds.forEach(id -> result.put(id, new HashSet<>()));
		if (assignIds.isEmpty()) {
			return result;
		}
		var res = client.call("mod_assign_get_submissions", Map.of("assignmentids", assignIds), SubmissionsResponse.class);
		for (var assignment : nullSafe(res.assignments())) {
			var submitted = result.computeIfAbsent(assignment.assignmentid(), k -> new HashSet<>());
			nullSafe(assignment.submissions()).stream()
					.filter(s -> SUBMITTED.equals(s.status()))
					.forEach(s -> submitted.add(s.userid()));
		}
		return result;
	}

	private static <T> List<T> nullSafe(List<T> list) {
		return list == null ? List.of() : list;
	}

	private static String describe(List<Warning> warnings) {
		var text = nullSafe(warnings).stream().map(Warning::message).filter(Objects::nonNull).collect(Collectors.joining("; "));
		return text.isEmpty() ? "" : " (" + text + ")";
	}

}
