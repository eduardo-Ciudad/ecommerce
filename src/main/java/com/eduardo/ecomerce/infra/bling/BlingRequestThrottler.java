package com.eduardo.ecomerce.infra.bling;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class BlingRequestThrottler {

    private static final long MIN_INTERVAL_MILLIS = 350; // um pouco acima de 333ms (3 req/s) por margem de segurança

    private final AtomicLong lastRequestTimestamp = new AtomicLong(0);

    public synchronized void throttle() {
        long now = System.currentTimeMillis();
        long last = lastRequestTimestamp.get();
        long elapsed = now - last;

        if (elapsed < MIN_INTERVAL_MILLIS) {
            try {
                Thread.sleep(MIN_INTERVAL_MILLIS - elapsed);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BlingIntegrationException("Interrompido durante throttle de requisição ao Bling", e);
            }
        }

        lastRequestTimestamp.set(System.currentTimeMillis());
    }
}