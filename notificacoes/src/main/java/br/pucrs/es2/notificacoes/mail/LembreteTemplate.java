package br.pucrs.es2.notificacoes.mail;

import br.pucrs.es2.notificacoes.notificacao.dto.SolicitacaoLembrete;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import org.springframework.web.util.HtmlUtils;

public final class LembreteTemplate {

	public record Email(String assunto, String texto, String html) {
	}

	private static final ZoneId BRASILIA = ZoneId.of("America/Sao_Paulo");
	private static final DateTimeFormatter PRAZO = DateTimeFormatter
			.ofPattern("EEEE, d 'de' MMMM 'de' yyyy 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
			.withZone(BRASILIA);

	private LembreteTemplate() {
	}

	// ex: sábado, 21 de novembro de 2026 às 12:30 (horário de Brasília)
	public static String formatarPrazo(long duedateEpoch) {
		return PRAZO.format(Instant.ofEpochSecond(duedateEpoch)) + " (horário de Brasília)";
	}

	public static Email montar(SolicitacaoLembrete s) {
		String prazo = formatarPrazo(s.duedateEpoch());
		String assunto = "Lembrete: a tarefa \"" + s.assignName() + "\" vence em breve";
		String texto = """
				Olá, %s!

				A tarefa "%s" da disciplina %s vence em %s e ainda não recebemos o seu envio.

				Acesse a tarefa: %s

				Este é um lembrete automático. Se já enviou, desconsidere esta mensagem.
				""".formatted(s.userFullname(), s.assignName(), s.courseName(), prazo, s.assignUrl());
		String html = """
				<p>Olá, %s!</p>
				<p>A tarefa <strong>%s</strong> da disciplina %s vence em <strong>%s</strong> e ainda não recebemos o seu envio.</p>
				<p><a href="%s">Acessar a tarefa</a></p>
				<p style="color:#666;font-size:12px">Este é um lembrete automático. Se já enviou, desconsidere esta mensagem.</p>
				""".formatted(esc(s.userFullname()), esc(s.assignName()), esc(s.courseName()), esc(prazo), esc(s.assignUrl()));
		return new Email(assunto, texto, html);
	}

	private static String esc(String value) {
		return HtmlUtils.htmlEscape(value, "UTF-8");
	}

}
