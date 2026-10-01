package com.shipfast.payment;

import com.shipfast.common.IdGenerator;
import com.shipfast.common.Money;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * In-memory stand-in for the card processor, used for local development, tests and the
 * load simulator. It mirrors the documented behaviour of the real processor's API:
 *
 * <ul>
 *   <li>charges are idempotent per {@code idempotencyKey}: replaying a key returns the original
 *       response and does not charge again;</li>
 *   <li>refunds are idempotent per key and may never exceed the captured amount;</li>
 *   <li>payment tokens starting with {@code tok_decline} are always declined.</li>
 * </ul>
 *
 * Network conditions can be made unreliable to exercise client error handling.
 */
public class SandboxPaymentGateway implements PaymentGateway {

    public record Capture(String transactionId, String reference, Money amount, String idempotencyKey) {
    }

    private final Object lock = new Object();
    private final Map<String, ChargeResponse> responsesByKey = new HashMap<>();
    private final Map<String, Capture> capturesByTransaction = new LinkedHashMap<>();
    private final Map<String, Money> refundsByKey = new HashMap<>();
    private final Map<String, Money> refundedByTransaction = new HashMap<>();
    private final IdGenerator ids = new IdGenerator(700_000);
    private final Random random;
    private final Clock clock;

    private double declineRate;
    private double connectTimeoutRate;
    private double readTimeoutRate;
    private Duration maxLatency = Duration.ZERO;
    private Instant outageStart;
    private Instant outageEnd;

    public SandboxPaymentGateway() {
        this(0L, Clock.systemUTC());
    }

    public SandboxPaymentGateway(long seed, Clock clock) {
        this.random = new Random(seed);
        this.clock = clock;
    }

    /** Fraction of charges declined by the issuing bank. */
    public SandboxPaymentGateway withDeclineRate(double rate) {
        this.declineRate = rate;
        return this;
    }

    /** Fraction of calls where the connection times out before the processor receives the request. */
    public SandboxPaymentGateway withConnectTimeoutRate(double rate) {
        this.connectTimeoutRate = rate;
        return this;
    }

    /** Fraction of calls where the processor handles the request but the response times out. */
    public SandboxPaymentGateway withReadTimeoutRate(double rate) {
        this.readTimeoutRate = rate;
        return this;
    }

    /** Each call sleeps for a random duration up to this long, to simulate network latency. */
    public SandboxPaymentGateway withMaxLatency(Duration latency) {
        this.maxLatency = latency;
        return this;
    }

    /** Every call made during [start, end) fails to connect. */
    public SandboxPaymentGateway withOutage(Instant start, Instant end) {
        this.outageStart = start;
        this.outageEnd = end;
        return this;
    }

    @Override
    public ChargeResponse charge(ChargeRequest request) {
        simulateLatency();
        if (inOutage()) {
            throw new GatewayTimeoutException("connect timed out (processor unavailable)");
        }
        synchronized (lock) {
            if (roll(connectTimeoutRate)) {
                throw new GatewayTimeoutException("connect timed out");
            }
            ChargeResponse response = responsesByKey.get(request.idempotencyKey());
            if (response == null) {
                response = process(request);
                responsesByKey.put(request.idempotencyKey(), response);
            }
            if (roll(readTimeoutRate)) {
                throw new GatewayTimeoutException("read timed out");
            }
            return response;
        }
    }

    private ChargeResponse process(ChargeRequest request) {
        if (request.paymentToken().startsWith("tok_decline") || roll(declineRate)) {
            return ChargeResponse.declined("card_declined");
        }
        String transactionId = ids.next("txn");
        capturesByTransaction.put(transactionId,
                new Capture(transactionId, request.reference(), request.amount(), request.idempotencyKey()));
        return ChargeResponse.approved(transactionId);
    }

    @Override
    public void refund(String transactionId, Money amount, String idempotencyKey) {
        synchronized (lock) {
            if (refundsByKey.containsKey(idempotencyKey)) {
                return;
            }
            Capture capture = capturesByTransaction.get(transactionId);
            if (capture == null) {
                throw new RefundRejectedException("Unknown transaction " + transactionId);
            }
            Money alreadyRefunded = refundedByTransaction.getOrDefault(transactionId, Money.ZERO);
            if (alreadyRefunded.plus(amount).greaterThan(capture.amount())) {
                throw new RefundRejectedException("Refund of " + amount + " would exceed captured amount "
                        + capture.amount() + " for " + transactionId + " (already refunded " + alreadyRefunded + ")");
            }
            refundsByKey.put(idempotencyKey, amount);
            refundedByTransaction.merge(transactionId, amount, Money::plus);
        }
    }

    public List<Capture> captures() {
        synchronized (lock) {
            return new ArrayList<>(capturesByTransaction.values());
        }
    }

    public List<Capture> capturesFor(String reference) {
        return captures().stream()
                .filter(capture -> capture.reference().equals(reference))
                .toList();
    }

    public Money refundedFor(String transactionId) {
        synchronized (lock) {
            return refundedByTransaction.getOrDefault(transactionId, Money.ZERO);
        }
    }

    private boolean inOutage() {
        if (outageStart == null) {
            return false;
        }
        Instant now = clock.instant();
        return !now.isBefore(outageStart) && now.isBefore(outageEnd);
    }

    private boolean roll(double probability) {
        return probability > 0 && random.nextDouble() < probability;
    }

    private void simulateLatency() {
        long max = maxLatency.toMillis();
        if (max <= 0) {
            return;
        }
        try {
            Thread.sleep(ThreadLocalRandom.current().nextLong(max + 1));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
