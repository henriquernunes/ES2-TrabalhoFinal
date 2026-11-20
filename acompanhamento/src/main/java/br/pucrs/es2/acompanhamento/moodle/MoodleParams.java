package br.pucrs.es2.acompanhamento.moodle;

import java.util.List;
import java.util.Map;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

// o rest do moodle quer os arrays assim: courseids[0]=2, options[0][name]=onlyactive
public final class MoodleParams {

	private MoodleParams() {
	}

	public static MultiValueMap<String, String> encode(Map<String, ?> params) {
		var out = new LinkedMultiValueMap<String, String>();
		params.forEach((key, value) -> walk(out, key, value));
		return out;
	}

	private static void walk(MultiValueMap<String, String> out, String key, Object value) {
		switch (value) {
			case null -> {
			}
			case List<?> list -> {
				for (int i = 0; i < list.size(); i++) {
					walk(out, key + "[" + i + "]", list.get(i));
				}
			}
			case Map<?, ?> map -> map.forEach((k, v) -> walk(out, key + "[" + k + "]", v));
			case Boolean b -> out.add(key, b ? "1" : "0");
			default -> out.add(key, String.valueOf(value));
		}
	}

}
