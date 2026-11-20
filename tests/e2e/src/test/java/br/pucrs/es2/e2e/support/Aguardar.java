package br.pucrs.es2.e2e.support;

import java.time.Duration;
import java.util.function.Predicate;
import java.util.function.Supplier;

public final class Aguardar {

	public static <T> T ate(String descricao, Supplier<T> fn, Predicate<T> pronto, Duration timeout) {
		long limite = System.nanoTime() + timeout.toNanos();
		T ultimo = null;
		while (System.nanoTime() < limite) {
			try {
				ultimo = fn.get();
				if (pronto.test(ultimo)) {
					return ultimo;
				}
			} catch (RuntimeException e) {
				// ainda subindo (ex: 503 do gateway), tenta de novo
			}
			dormir(Duration.ofSeconds(1));
		}
		String valor = String.valueOf(ultimo);
		throw new AssertionError("timeout esperando: " + descricao + " (último valor: " + valor.substring(0, Math.min(500, valor.length())) + ")");
	}

	public static <T> T ate(String descricao, Supplier<T> fn, Predicate<T> pronto) {
		return ate(descricao, fn, pronto, Duration.ofSeconds(60));
	}

	public static void dormir(Duration d) {
		try {
			Thread.sleep(d);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(e);
		}
	}

	private Aguardar() {
	}

}
