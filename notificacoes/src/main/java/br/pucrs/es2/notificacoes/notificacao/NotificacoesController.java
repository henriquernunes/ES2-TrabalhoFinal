package br.pucrs.es2.notificacoes.notificacao;

import br.pucrs.es2.notificacoes.notificacao.dto.NotificacaoResponse;
import br.pucrs.es2.notificacoes.notificacao.dto.ReprocessarResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "notificacoes")
@RestController
@RequestMapping("/notificacoes")
public class NotificacoesController {

	private final NotificacoesService service;

	public NotificacoesController(NotificacoesService service) {
		this.service = service;
	}

	@Operation(summary = "Lista o registro de notificações (mais recentes primeiro)")
	@ApiResponse(responseCode = "400", description = "status inválido")
	@GetMapping
	public List<NotificacaoResponse> listar(@RequestParam(required = false) StatusNotificacao status) {
		return service.listar(status);
	}

	@Operation(summary = "Republica a solicitação original na fila principal")
	@ApiResponse(responseCode = "202", description = "Republicada")
	@ApiResponse(responseCode = "404", description = "Chave não encontrada")
	@PostMapping("/{key}/reprocessar")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public ReprocessarResponse reprocessar(@PathVariable String key) {
		return service.reprocessar(key);
	}

}
