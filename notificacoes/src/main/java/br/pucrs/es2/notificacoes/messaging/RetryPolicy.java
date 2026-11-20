package br.pucrs.es2.notificacoes.messaging;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

// backoff: base, 2x, 4x... depois de maxRetries vai pra dlq
public final class RetryPolicy {

	public sealed interface Decisao {
	}

	public record NovaTentativa(int tentativa, Duration atraso, String fila) implements Decisao {
	}

	public record MoverParaDlq() implements Decisao {
	}

	private RetryPolicy() {
	}

	public static Duration atraso(int retryCount, Duration base) {
		return base.multipliedBy(1L << retryCount);
	}

	public static List<Duration> atrasos(int maxRetries, Duration base) {
		return IntStream.range(0, maxRetries).mapToObj(i -> atraso(i, base)).toList();
	}

	// retryCount = quantas vezes já tentou de novo
	public static Decisao decidir(int retryCount, int maxRetries, Duration base) {
		if (retryCount >= maxRetries) {
			return new MoverParaDlq();
		}
		Duration atraso = atraso(retryCount, base);
		return new NovaTentativa(retryCount + 1, atraso, Filas.retryQueue(atraso));
	}

}
