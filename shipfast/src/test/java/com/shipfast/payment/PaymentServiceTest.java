package com.shipfast.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.shipfast.common.IdGenerator;
import com.shipfast.common.Money;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class PaymentServiceTest {

    /** Gateway that replays a script of responses and records every request it receives. */
    static final class ScriptedGateway implements PaymentGateway {

        final Deque<Supplier<ChargeResponse>> script = new ArrayDeque<>();
        final List<ChargeRequest> requests = new ArrayList<>();

        ScriptedGateway then(Supplier<ChargeResponse> step) {
            script.add(step);
            return this;
        }

        ScriptedGateway thenTimeout() {
            return then(() -> {
                throw new GatewayTimeoutException("connect timed out");
            });
        }

        @Override
        public ChargeResponse charge(ChargeRequest request) {
            requests.add(request);
            return script.removeFirst().get();
        }

        @Override
        public void refund(String transactionId, Money amount, String idempotencyKey) {
            throw new UnsupportedOperationException();
        }
    }

    private final List<Duration> sleeps = new ArrayList<>();
    private final Sleeper recordingSleeper = sleeps::add;

    private PaymentService service(PaymentGateway gateway) {
        return new PaymentService(gateway, RetryPolicy.defaults(), recordingSleeper, new IdGenerator());
    }

    @Test
    void approvedChargeReturnsTransaction() {
        ScriptedGateway gateway = new ScriptedGateway().then(() -> ChargeResponse.approved("txn-1"));
        PaymentOutcome outcome = service(gateway).charge("ord-1", "tok_visa", Money.of("20.00"));

        assertTrue(outcome.approved());
        assertEquals("txn-1", outcome.transactionId());
        assertEquals(1, outcome.attempts());
        assertEquals("ord-1", gateway.requests.get(0).reference());
    }

    @Test
    void declineIsNotRetried() {
        ScriptedGateway gateway = new ScriptedGateway().then(() -> ChargeResponse.declined("insufficient_funds"));
        PaymentOutcome outcome = service(gateway).charge("ord-1", "tok_visa", Money.of("20.00"));

        assertFalse(outcome.approved());
        assertEquals("insufficient_funds", outcome.declineCode());
        assertEquals(1, gateway.requests.size());
    }

    @Test
    void retriesAfterTimeoutAndSucceeds() {
        ScriptedGateway gateway = new ScriptedGateway()
                .thenTimeout()
                .then(() -> ChargeResponse.approved("txn-2"));
        PaymentOutcome outcome = service(gateway).charge("ord-1", "tok_visa", Money.of("20.00"));

        assertTrue(outcome.approved());
        assertEquals("txn-2", outcome.transactionId());
        assertEquals(2, outcome.attempts());
    }

    @Test
    void givesUpAfterMaxAttempts() {
        ScriptedGateway gateway = new ScriptedGateway().thenTimeout().thenTimeout().thenTimeout();

        assertThrows(PaymentUnavailableException.class,
                () -> service(gateway).charge("ord-1", "tok_visa", Money.of("20.00")));
        assertEquals(3, gateway.requests.size());
    }

    @Test
    void backsOffExponentiallyBetweenAttempts() {
        ScriptedGateway gateway = new ScriptedGateway()
                .thenTimeout()
                .thenTimeout()
                .then(() -> ChargeResponse.approved("txn-3"));
        service(gateway).charge("ord-1", "tok_visa", Money.of("20.00"));

        assertEquals(List.of(Duration.ofMillis(200), Duration.ofMillis(400)), sleeps);
    }

    @Test
    void rejectsNonPositiveAmounts() {
        ScriptedGateway gateway = new ScriptedGateway();
        assertThrows(IllegalArgumentException.class, () -> service(gateway).charge("ord-1", "tok_visa", Money.ZERO));
        assertTrue(gateway.requests.isEmpty());
    }
}
