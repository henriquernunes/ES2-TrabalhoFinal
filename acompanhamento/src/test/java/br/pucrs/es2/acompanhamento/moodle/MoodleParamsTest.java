package br.pucrs.es2.acompanhamento.moodle;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MoodleParamsTest {

	@Test
	void usaOFormatoDeArraysDoMoodle() {
		var params = new LinkedHashMap<String, Object>();
		params.put("courseids", List.of(2, 3));
		params.put("includenotenrolledcourses", true);
		params.put("options", List.of(Map.of("name", "onlyactive")));
		params.put("ignorado", null);

		assertThat(MoodleParams.encode(params).toSingleValueMap()).containsExactly(
				Map.entry("courseids[0]", "2"),
				Map.entry("courseids[1]", "3"),
				Map.entry("includenotenrolledcourses", "1"),
				Map.entry("options[0][name]", "onlyactive"));
	}

}
