package br.pucrs.es2.acompanhamento.reengajamento;

import br.pucrs.es2.acompanhamento.reengajamento.dto.CicloResultado;
import br.pucrs.es2.acompanhamento.reengajamento.dto.StatusResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "reengajamento")
@RestController
public class ReengajamentoController {

	private final ReengajamentoService service;

	public ReengajamentoController(ReengajamentoService service) {
		this.service = service;
	}

	@Operation(summary = "Dispara um ciclo de verificação manualmente")
	@ApiResponse(responseCode = "200", description = "Resultado do ciclo")
	@ApiResponse(responseCode = "409", description = "Já existe um ciclo em execução")
	@PostMapping("/run")
	public CicloResultado run() {
		return service.runCycle("manual");
	}

	@Operation(summary = "Resultado do último ciclo e configuração atual")
	@GetMapping("/status")
	public StatusResponse status() {
		return service.status();
	}

}
