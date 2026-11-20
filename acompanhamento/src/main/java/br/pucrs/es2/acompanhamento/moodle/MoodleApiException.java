package br.pucrs.es2.acompanhamento.moodle;

public class MoodleApiException extends RuntimeException {

	private final String wsfunction;
	private final String errorcode;

	public MoodleApiException(String message, String wsfunction, String errorcode) {
		super(message);
		this.wsfunction = wsfunction;
		this.errorcode = errorcode;
	}

	public MoodleApiException(String message, String wsfunction, String errorcode, Throwable cause) {
		super(message, cause);
		this.wsfunction = wsfunction;
		this.errorcode = errorcode;
	}

	public String getWsfunction() {
		return wsfunction;
	}

	public String getErrorcode() {
		return errorcode;
	}

}
