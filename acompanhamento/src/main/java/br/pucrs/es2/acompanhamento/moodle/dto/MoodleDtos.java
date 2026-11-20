package br.pucrs.es2.acompanhamento.moodle.dto;

import java.util.List;

// só os campos que a gente usa, com o mesmo nome do json do moodle
public final class MoodleDtos {

	private MoodleDtos() {
	}

	// duedate em segundos, 0 = sem prazo
	public record Assignment(long id, long cmid, long course, String name, long duedate) {
	}

	public record Course(long id, String fullname, List<Assignment> assignments) {
	}

	public record Warning(String item, Long itemid, String warningcode, String message) {
	}

	public record AssignmentsResponse(List<Course> courses, List<Warning> warnings) {
	}

	public record Role(long roleid, String shortname, String name) {
	}

	public record EnrolledUser(long id, String fullname, String email, List<Role> roles) {
	}

	public record Submission(long userid, String status) {
	}

	public record AssignmentSubmissions(long assignmentid, List<Submission> submissions) {
	}

	public record SubmissionsResponse(List<AssignmentSubmissions> assignments, List<Warning> warnings) {
	}

}
