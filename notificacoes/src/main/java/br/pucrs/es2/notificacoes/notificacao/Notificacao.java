package br.pucrs.es2.notificacoes.notificacao;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

// tabela criada pelo flyway (V1)
@Entity
@Table(name = "notificacoes")
public class Notificacao {

	private static final int MAX_ERRO = 2000;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "idempotency_key", nullable = false, unique = true, length = 100)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 10)
	private StatusNotificacao status = StatusNotificacao.PENDENTE;

	@Column(nullable = false)
	private int tentativas;

	@Column(name = "ultimo_erro", length = MAX_ERRO)
	private String ultimoErro;

	@Column(name = "user_email", nullable = false, length = 320)
	private String userEmail;

	@Column(name = "assign_name", nullable = false, length = 500)
	private String assignName;

	// json original, usado no reprocessar
	@Column(nullable = false, length = 10000)
	private String payload;

	@Column(name = "criado_em", nullable = false)
	private Instant criadoEm;

	@Column(name = "atualizado_em", nullable = false)
	private Instant atualizadoEm;

	@Column(name = "enviado_em")
	private Instant enviadoEm;

	protected Notificacao() {
	}

	public Notificacao(String idempotencyKey, String userEmail, String assignName, String payload, Instant agora) {
		this.idempotencyKey = idempotencyKey;
		this.userEmail = userEmail;
		this.assignName = assignName;
		this.payload = payload;
		this.criadoEm = agora;
		this.atualizadoEm = agora;
	}

	// FALHOU não é final, uma nova solicitação reabre
	public void reabrir(String payload, Instant agora) {
		this.status = StatusNotificacao.PENDENTE;
		this.payload = payload;
		this.atualizadoEm = agora;
	}

	public void registrarTentativa(Instant agora) {
		this.tentativas++;
		this.atualizadoEm = agora;
	}

	public void marcarEnviado(Instant agora) {
		this.status = StatusNotificacao.ENVIADO;
		this.ultimoErro = null;
		this.enviadoEm = agora;
		this.atualizadoEm = agora;
	}

	public void registrarErro(String erro, Instant agora) {
		this.ultimoErro = erro == null || erro.length() <= MAX_ERRO ? erro : erro.substring(0, MAX_ERRO);
		this.atualizadoEm = agora;
	}

	public void marcarFalhou(String erro, Instant agora) {
		registrarErro(erro, agora);
		this.status = StatusNotificacao.FALHOU;
	}

	public boolean isEnviado() {
		return status == StatusNotificacao.ENVIADO;
	}

	public Long getId() {
		return id;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public StatusNotificacao getStatus() {
		return status;
	}

	public int getTentativas() {
		return tentativas;
	}

	public String getUltimoErro() {
		return ultimoErro;
	}

	public String getUserEmail() {
		return userEmail;
	}

	public String getAssignName() {
		return assignName;
	}

	public String getPayload() {
		return payload;
	}

	public Instant getCriadoEm() {
		return criadoEm;
	}

	public Instant getAtualizadoEm() {
		return atualizadoEm;
	}

	public Instant getEnviadoEm() {
		return enviadoEm;
	}

}
