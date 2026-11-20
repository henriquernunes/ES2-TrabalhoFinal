package br.pucrs.es2.notificacoes.notificacao;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificacaoRepository extends JpaRepository<Notificacao, Long> {

	Optional<Notificacao> findByIdempotencyKey(String idempotencyKey);

	List<Notificacao> findAllByOrderByCriadoEmDescIdDesc();

	List<Notificacao> findByStatusOrderByCriadoEmDescIdDesc(StatusNotificacao status);

}
