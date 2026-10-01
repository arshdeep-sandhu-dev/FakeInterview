# Interviewer Guide: SPOILERS

Answer key for the ShipFast mock. Read only the section for the ticket you just did, or all of it
if you're playing the interviewer for a friend.

Files in this folder:

| File                    | What it is                                                                   |
|-------------------------|------------------------------------------------------------------------------|
| `INTERVIEWER_GUIDE.md`  | This guide: root causes, traps, edge cases, follow-up questions, rubric     |
| `ReproTests.java`       | Reference reproduction and edge-case tests for all 8 tickets (19 tests)     |
| `reference-fix.patch`   | One correct fix for all 8 tickets. From `FakeInterview/`: `patch -p1 < interviewer/reference-fix.patch` |

**Verified behaviour** (checked when this mock was built):

- Baseline: 87/87 existing tests pass. `sim` fails 10-11 of 11 reconciliation checks.
- `ReproTests` on the baseline: 15 fail. The 4 that pass are guard tests that exist to catch
  plausible-but-wrong fixes.
- Baseline + `reference-fix.patch`: 106/106 tests pass, and `sim` shows **0 of 11 failed on 12/12
  seeds**.
- The "masking" fixes called out below were tried as well. Where this guide says a mask "still
  fails X", that was observed, not guessed.

---

## Running the session (interviewer script)

| Time      | Phase                     | What you're watching for                                                   |
|-----------|---------------------------|----------------------------------------------------------------------------|
| 0-5 min   | Read ticket, restate      | Do they pull out the clues (timing, "only sometimes", "can't reproduce")?  |
| 5-15      | Reproduce + investigate   | Do they reproduce before theorising? Do they make the agent explain first? |
| 15-25     | Failing test              | Do they **run it and show it red** before fixing?                          |
| 25-35     | Fix                       | Small diff? Do they push back on a symptom-level fix?                      |
| 35-50     | Verify + edge cases       | Revert-check, full suite, sim, edge cases the agent didn't propose         |
| 50-60     | Integration + wrap-up     | Existing bad data, rollout, monitoring, the same pattern elsewhere         |

Stay mostly quiet during the work. Nudge only if they're stuck for more than 5 minutes (use the
hints in each ticket's section). Save the follow-up questions for phases 5-6. Ask
**"How did you know it was correct?"** every single time.

### Questions for every ticket
- "How did you know it was correct?"
- "Would your test have failed before the fix? Show me." (They should revert just the fix.)
- "Is that the root cause, or did you just make this symptom go away?"
- "What edge cases did you add that the AI didn't suggest?"
- "Where else in the codebase could this same pattern exist?"
- "What happens to customers or records that were already affected?"
- "What would you monitor after deploying?"
- "What would you do if this fix hadn't worked?"

### Rubric (score each 1-4)

| Area                          | 1 (weak)                                   | 4 (strong)                                                                   |
|-------------------------------|--------------------------------------------|------------------------------------------------------------------------------|
| Problem framing               | Pastes the ticket straight into the agent  | Restates the symptom, extracts clues, forms hypotheses before prompting      |
| Driving the agent             | Accepts the first answer and edit          | Asks for explanation first, verifies claims against code, pushes back        |
| Reproduction                  | No test, or the test never ran red         | Failing test from the ticket's example, shown red for the right reason       |
| Root cause vs. mask           | Clamp / null-check / config tweak          | Fixes the mechanism, and explains why the tempting fix would only mask it    |
| Edge cases                    | Only the ticket's example                  | Boundaries, every exit path, concurrency, DST, scale; beyond the AI's list   |
| Verification                  | "Tests pass"                               | Revert-check, full suite, sim across seeds, repeated concurrency runs        |
| Plan B                        | Stuck when the fix doesn't work            | Named next hypotheses up front; treats "unit green, sim red" as a 2nd cause   |
| Integration                   | Stops at the diff                          | Data repair, rollout, monitoring, the same pattern elsewhere, follow-up tickets |
| Communication                 | Silent or rambling                         | Narrates decisions and trade-offs, flags product questions as product questions |

Strong candidates score mostly 3s and 4s. **The fix itself is maybe 20% of the grade.**

---

## Overview of the bugs

| Ticket  | Difficulty | Root cause (one line)                                                              | Main trap                                             |
|---------|:----------:|------------------------------------------------------------------------------------|-------------------------------------------------------|
| SUP-101 | Easy, then hard | `>` instead of `>=` in the coupon limit check, **plus** check-then-act across checkout | Fixing only the off-by-one; sim still shows 101-103   |
| SUP-102 | Medium     | New idempotency key generated on **every retry**                                  | Turning retries off; UUID still inside the loop       |
| SUP-103 | Hard       | Non-atomic read-check-write on `ConcurrentHashMap` in reserve/release/commit        | `synchronized` on `reserve()` only                    |
| SUP-104 | Medium     | No rollback of reservations on out-of-stock (partial) or payment exception         | The daily reset script; releasing lines never reserved |
| SUP-105 | Easy to find, medium to fix | Inclusive end at `23:59:59`, which drops the last second of each day | `start.plus(1 day)` breaks on DST days                |
| SUP-106 | Medium     | `OrderLine` reads the **live** catalog price instead of the price at purchase      | Snapshotting only the order total                     |
| SUP-107 | Medium     | Audit listener NPE on bulk discounts **and** the event bus doesn't isolate handler failures | A null check only; reordering listeners         |
| SUP-108 | Medium (sneaky) | `Money.equals` uses `BigDecimal.equals` (scale-sensitive)                    | Fixing only `RefundService`; changing `equals` without `hashCode` |

Bugs interact on purpose: SUP-103 contributes to SUP-104's symptom, and SUP-106 and SUP-108 both
break "Refund in full". A candidate who fixes one and sees another sim line stay red, then says
"that's a different cause, here's how I'd confirm it," is showing exactly the right instinct.

---

## SUP-101: Coupon redeemed more times than its limit

**Root cause 1: off-by-one.**
[`CouponService.java:41`](../shipfast/src/main/java/com/shipfast/pricing/CouponService.java#L41)

```java
if (coupon.isLimited() && coupon.getTimesUsed() > coupon.getMaxUses()) {   // should be >=
```

`timesUsed` counts completed redemptions. With `maxUses = 3`, after 3 uses `3 > 3` is false, so
a 4th use is allowed. This exactly matches QA's "4 times, never 5".

**Root cause 2: check-then-act across the whole checkout.** `validate()` runs at the start of
`placeOrder`, and `redeem()` runs after payment. Under load, many checkouts see `timesUsed = 99`
at the same moment and all pass. The off-by-one explains +1. The race explains production's 103.

**Hints if stuck:** "QA got exactly one extra. What does that suggest?" and later, "Production
got three extra. Does your fix explain that?"

**Diagnostic:** `sim --threads 1` gives **exactly 101** redemptions (only the off-by-one), while
8 threads gives 101-103. Comparing the two separates the two causes cleanly.

**Reproduction test:** `T1_Coupon.couponCanBeUsedExactlyMaxUsesTimes` (validate + redeem 3
times, then the 4th validate must throw).

**Why the existing test didn't catch it:** `CouponServiceTest.limitedCouponIsRejectedOnceUsedUp`
redeems **5** times against a limit of 3. It never tests the boundary. Good candidates point this
out.

**Tempting fixes that mask it:**
- Setting the Cyber Monday coupon's limit to 499 ("limit minus one"). This changes config to
  compensate for a code bug.
- Fixing only `>` to `>=`. The unit test goes green, but the sim still shows **101-103** (observed
  on 4 of 5 seeds). This is the "unit green, sim red, so there's a second cause" moment.
- Adding `synchronized` on `validate()`. It doesn't help, because the window is *between*
  `validate()` and `redeem()`.

**Correct fix:**
1. `>=`.
2. Atomically **claim** a use at checkout, using a CAS loop on the `AtomicInteger` that fails when
   `used >= maxUses`, and **release** it on every failure path (declined, out of stock, exception).
   In a real DB:
   `UPDATE coupons SET used = used + 1 WHERE code = ? AND used < max_uses` and check rows affected.

**Edge cases a strong candidate adds:**
- `maxUses = 1` (a single-use coupon).
- `maxUses = 0` means **unlimited**. A rewrite that drops the `isLimited()` guard makes
  `0 >= 0` reject every unlimited coupon. `unlimitedCouponNeverRunsOut` catches this.
- A declined payment must not consume a use (the existing test `couponIsNotRedeemedWhenPaymentDeclined`).
- Concurrent checkouts: `T1_Coupon.concurrentCheckoutsNeverExceedLimit` (32 threads, limit 5,
  gateway latency to widen the window).

**Integration:**
- The 3 extra redemptions already happened. Honouring them is a **business** decision (Marketing),
  not an engineering one.
- Monitor redemptions vs. `maxUses`, and alert if they're ever exceeded.
- Product questions: does a **refunded** order give the coupon use back? Should an in-flight
  (unpaid) checkout hold a use?

**Follow-ups:**
- *"Will Cyber Monday's limit of 500 hold?"* Only after both fixes. Show the concurrent test and
  the sim.
- *"Why not lock the whole checkout?"* It serializes every order in the store just to protect one
  counter.

---

## SUP-102: Customers charged twice

**Root cause:**
[`PaymentService.java:36`](../shipfast/src/main/java/com/shipfast/payment/PaymentService.java#L36)
builds a `ChargeRequest` with `ids.next("chg")` **inside** the retry loop, so every attempt has a
new idempotency key. When the processor **handles the charge but the response times out** (a read
timeout), the retry arrives with a new key. The processor treats it as a new charge, so the
customer is charged twice. The processor's idempotency works as documented. We defeated it.

**Why nobody could reproduce it:** it takes a lost response, not a failed request.
`PaymentServiceTest.retriesAfterTimeoutAndSucceeds` scripts a timeout **before** processing, so
the retry is harmless. The test also never looks at the keys. `SandboxPaymentGateway` documents
both `connectTimeoutRate` and `readTimeoutRate`. Good candidates read that and connect it to the
ticket.

**Hints if stuck:** "The processor says idempotency worked as documented. What does idempotency
depend on from *our* side?"

**Reproduction test:** `T2_DoubleCharge.lostResponseRetryDoesNotChargeTwice`. A wrapper gateway
lets the sandbox process the first charge and then throws `GatewayTimeoutException`. The test
asserts exactly one capture, and that the returned transaction id is that capture's.

**Tempting fixes that mask it:**
- **Turning off retries** (`maxAttempts = 1`). Double charges stop, but every transient timeout
  becomes a failed checkout (503), and SUP-104's leak gets worse. This trades one incident for
  another.
- **Moving to `UUID.randomUUID()` but leaving it inside the loop.** AI agents do this often. It's
  still a new key per attempt, so nothing changes. Watch whether the candidate notices.
- Nightly job that refunds duplicate captures. Customers still see two charges, and we still pay
  fees and chargebacks.
- Only retrying on connect timeouts. That's a defensible policy, but then a read timeout is an
  unknown outcome with no safe retry.

**Correct fix:** create the key **once per payment intent, before the loop**, derived from
something stable, e.g. `"charge-" + orderId`. It must be the same across retries and unique per
order.

**Edge cases a strong candidate adds:**
- Different orders get different keys (`differentOrdersGetDifferentKeys`, a guard test).
- Key stability across a **process restart**. Deriving the key from the order id survives a
  restart. A random UUID created before the loop only protects in-process retries.
- **A declined card, then the customer retries with a new card.** If the key is only the order id,
  the processor may replay the cached decline. The key should cover the *payment attempt*
  (order id + payment method or a user-attempt counter). That's fine in this codebase, since every
  checkout creates a new order, but saying it out loud is a strong signal.
- **Double-clicking "Place order"** creates two orders, two keys and two charges. That needs a
  client-supplied checkout token and is a separate ticket.
- **All retries time out after the charge was processed.** The customer is charged and sees a 503,
  and there is no order. The robust design is to persist the order as `PAYMENT_PENDING` before
  charging, then reconcile with the processor by key. The sim's "Every charge belongs to a paid
  order" check covers this. It's rare, and was clean on all 12 verified seeds.

**Integration:**
- Refund the existing duplicates: find processor captures that share an order reference (the sim
  prints the over-collected total).
- Check the processor's key-length and key-retention limits.
- Alert on more than one capture per order reference.

---

## SUP-103: Oversold the limited sneaker

**Root cause:**
[`InventoryService.reserve`](../shipfast/src/main/java/com/shipfast/inventory/InventoryService.java#L53)
does `available.get(sku)`, then checks the value, then `available.put(sku, remaining)`. Each call
is thread-safe on its own, but the **sequence isn't atomic**. Two threads both read `1`, both
write `0`, and both succeed. `ConcurrentHashMap` gives a false sense of safety here.

The low-stock listener runs **between the get and the put** and writes to a `CopyOnWriteArrayList`
audit log. That widens the window exactly when stock is low, which is exactly during a sell-out.

The **same pattern** is in `release()` (lines 69-74) and `commit()` (lines 81-86):
`reserved.getOrDefault` followed by `reserved.put(held - quantity)` loses updates. That's the
ticket's clue that ordinary SKUs are off "in **both** directions."

**Why QA couldn't reproduce it:** two browsers aren't concurrent enough. It needs many threads on
one SKU in the same few microseconds.

**Hints if stuck:** "`./sim.sh --threads 1`. Does it still happen?" (Observed: with one thread the
ledger check turns `[ OK ]`, which proves a race. The "stuck in reserved" check stays red, which
proves SUP-104 is a separate, non-concurrency cause.)

**Reproduction tests:**
- `T3_Oversell.concurrentReservationsNeverOversell`: stock 40, 16 threads × 10 attempts, a start
  latch, 50 rounds. It asserts exactly 40 successes and the snapshot `(40, 0, 40, 0)`.
- `T3_Oversell.concurrentReleaseAndCommitKeepLedgerBalanced`: 16 threads alternating
  reserve/commit and reserve/release. It asserts `reserved == 0` and a balanced ledger.

Both failed on 2/2 baseline runs. They're probabilistic, and a candidate should say so.

**Tempting fixes that mask it:**
- **`synchronized` on `reserve()` only.** The oversell test goes green, but the release/commit
  ledger test **still fails** and the sim ledger stays unbalanced (observed).
  `release`/`commit`/`receive` still write the same maps without the lock.
- Clamping at zero (`Math.max(0, ...)`). Nothing ever goes negative here. It's a lost update, so
  clamping does nothing.
- "Re-read after the put and roll back if negative." Still racy.
- Making the listener async or faster. That shrinks the window without closing it.
- A nightly job that "fixes" the ledger.

**Correct fix (the options are part of the discussion):**
1. `synchronized` on **every** mutator (`receive`, `reserve`, `release`, `commit`) and on
   `snapshot`. Simple and correct, but one lock covers every SKU. (This is what the reference patch
   does.)
2. A lock per SKU (a `StockLevel` object per SKU, or striped locks). Better throughput.
3. `available.compute(...)` makes single-map updates atomic, but `reserve` touches **two** maps.
   Readers can see an in-between state, so "no oversell" holds but a snapshot can be momentarily
   off.
4. With a real DB: `UPDATE stock SET available = available - ? WHERE sku = ? AND available >= ?`,
   then check that one row was affected.

Bonus nuance: avoid calling listeners (outside code) while holding the lock. Collect the alerts
and fire them after releasing it.

**Edge cases a strong candidate adds:**
- **A restock during the sale.** `receive()` uses `merge`, but if it isn't locked with the others,
  the `put` in `reserve` can wipe out a delivery.
- Multi-unit reservations near the boundary (3 left, two requests for 2).
- Concurrent commit vs. release on the same SKU.
- How they talk about **evidence**: "Failed 50/50 rounds before, passed 200/200 after, clean on 12
  sim seeds. That's strong evidence, not proof. jcstress would be the rigorous tool."

**Integration:**
- Locking costs throughput on hot SKUs. Measure it with the sim (`--shoppers 5000`).
- Data repair needs a physical recount, then a ledger adjustment. Oversold customers need
  cancellations and refunds.
- Run the invariant `received == available + reserved + sold` as an **alert**, not an auto-fix.

---

## SUP-104: Stock stuck in "reserved"

**Root causes (two exit paths with no rollback)** in
[`OrderService.placeOrder`](../shipfast/src/main/java/com/shipfast/order/OrderService.java#L82):

1. **Partial reservation.** `reserveStock` reserves line by line. If line N is out of stock, it
   throws `OutOfStockException` and lines 1..N-1 **stay reserved**. That's the "after the sneaker
   sold out" clue: carts with the sneaker plus other items leak the other items.
2. **Payment exception.** `payments.charge(...)` (line 85) can throw
   `PaymentUnavailableException`. Only the *declined* path releases stock. That's the "23:58
   brown-out" clue.

A third contributor: SUP-103's lost updates in `release`/`commit` also strand units. If a
candidate fixes both paths but not SUP-103, the sim still shows a **smaller** stuck count. That's a
good "second cause" moment.

**Hints if stuck:** "List every way `placeOrder` can exit after `reserveStock` starts. Which ones
release?"

**Reproduction tests:** `T4_Leaks.partialReservationRolledBackWhenLaterItemOutOfStock` (MUG then
LAMP×21) and `T4_Leaks.stockReleasedWhenProcessorUnavailable` (`connectTimeoutRate = 1.0`).

**Tempting fixes that mask it:**
- **The morning reset script from the ticket.** It also wipes legitimate in-flight reservations,
  and units vanish from the ledger.
- A reservation-expiry job. Good defence in depth, but it hides the bug. Keep it as a safety net,
  not the fix.
- "Check stock for all lines first, then reserve." Still check-then-act (see SUP-103).
- **Wrapping everything in `catch (Exception)` and releasing *all* lines.** This releases lines
  that were never reserved. Because `reserved` is a per-SKU counter, not per-order, that can
  release **another customer's** reservation, or throw from `release`. Only release what *this*
  order reserved.

**Correct fix:** track what was actually reserved, and in a `try/catch` release exactly those on
any failure before commit. (With the SUP-101 fix, release the coupon claim too.)

**The deep question:** *"Is releasing stock on `PaymentUnavailableException` actually correct?"*
A **read** timeout means the charge *might* have gone through. If we release the stock and the
customer was charged, we've sold stock we gave back. The robust design is to save the order as
`PAYMENT_PENDING` before charging, keep the reservation when the outcome is unknown, and settle it
asynchronously by asking the processor about the idempotency key (safe once SUP-102 is fixed).
Candidates who raise this unprompted are excellent.

**Edge cases:**
- The first line fails, so there's nothing to release.
- Duplicate SKUs merged into one line, so the release quantity must match.
- `release` itself throws during rollback. Don't lose the original exception; use
  `addSuppressed`.

**Integration:**
- One-time repair: there are no in-flight orders, so leaked `reserved` goes back to `available`.
- Retire the morning script only after the invariant alert has been quiet for a while.
- Monitor for reserved units with no in-flight order older than N minutes.

---

## SUP-105: Daily report short vs. processor settlement

**Root cause:**
[`DailySalesReport.java:51`](../shipfast/src/main/java/com/shipfast/reporting/DailySalesReport.java#L51)

```java
Instant end = date.atTime(LocalTime.of(23, 59, 59)).atZone(storeZone).toInstant();
... !order.getPlacedAt().isAfter(end)
```

The window is the closed interval `[00:00:00, 23:59:59.000]`. Order timestamps carry milliseconds,
so anything in `(23:59:59.000, 24:00:00)` falls in the gap. It's after today's end and before
tomorrow's start, so it's in **neither** report. Black Friday was worse because of the midnight rush
(the coupon and sale prices ended at midnight).

**Reproduction test:** `T5_Report.lastSecondOfTheDayIsIncluded`, using the ticket's own timestamp
`23:59:59.412`. Using the ticket's example data verbatim is a good habit.

**Tempting fixes that mask it:**
- `LocalTime.of(23, 59, 59, 999_000_000)`. It closes the millisecond gap but not a nanosecond one.
  `LocalTime.MAX` technically works, but inclusive-end intervals are fragile.
- `end.plusSeconds(1)` with an inclusive check. An order at exactly `00:00:00.000` gets counted in
  **both** days. The guard test `midnightBelongsToNextDayOnly` catches it.
- **`start.plus(Duration.ofDays(1))` or `Instant.plus(1, DAYS)`.** AI agents suggest this often.
  It's always 24 hours, so it's **wrong on DST days**. Verified: it fails `fallBackDayHas25Hours`
  (2026-11-01, where 23:30 is dropped) and `springForwardDayHas23Hours` (2026-03-08, where
  next-day 00:30 is included).

**Correct fix:** a half-open interval in the store's zone:

```java
Instant start = date.atStartOfDay(storeZone).toInstant();
Instant end   = date.plusDays(1).atStartOfDay(storeZone).toInstant();
... !placedAt.isBefore(start) && placedAt.isBefore(end)
```

**Edge cases:** midnight exactly, both DST transitions, the last millisecond and last nanosecond,
and orders with identical timestamps. A product question: should refunds count on the **order's**
date or the **refund's** date? (The report currently uses the order's date.)

**Integration:** restate the affected historical reports (finance needs to know which months
changed), grep for the same idiom elsewhere (`23, 59, 59`, `LocalTime.MAX`), and add a daily
reconciliation of report count vs. processor captures. Note that SUP-106 also makes this report's
dollar figures drift.

---

## SUP-106: Order total differs from receipt; "Refund in full" fails

**Root cause:**
[`OrderLine.java:33`](../shipfast/src/main/java/com/shipfast/order/OrderLine.java#L33).
`unitPrice()` returns `product.getPrice()`, the **live** catalog price. `Order.getSubtotal()` and
`getTotal()` recompute from lines on every call, while discount, tax and shipping are stored
snapshots. When sale prices reverted at midnight, Friday's orders started showing the new subtotal
with the old tax and discount.

What depends on it:
- The **refund cap** (`RefundService` uses `getTotal()`), so "refund in full" exceeds the captured
  amount and the processor rejects it.
- The **report's category totals** (`lineTotal()`), which change on every re-run.
- The **receipt is correct**, because it was built from the event at placement time. That
  contrast is the key clue.

**Hints if stuck:** "What changed between Saturday and Monday? Only for orders before midnight?"

**Reproduction tests:** `T6_PriceDrift.orderTotalUnaffectedByLaterPriceChange` and
`fullRefundAfterPriceIncreaseSucceeds`.

**Tempting fixes that mask it:**
- **Storing only `total` on `Order`.** Refunds and the order page are fixed, but line prices, the
  subtotal and the report's category totals still drift.
- Refunding against the processor's captured amount. That fixes refunds only.
- Caching the report output.
- "Don't change prices during a sale." That's a process change, not a fix.

**Correct fix:** `OrderLine` captures `unitPrice` (and arguably the product name) when it's
constructed. The order is a record of what was sold, at that moment.

**Alternative worth discussing:** make `Product` immutable and have the catalog *replace* the
object on a price change. Old lines would keep the old instance. That works, but it's implicit. An
explicit snapshot states the intent.

**Edge cases:**
- A price change **during** checkout. The quote and the order must use the same snapshot. After
  the fix both read the line's captured price.
- A price **drop** after purchase. The customer should get back what they paid, not the new lower
  price.
- A product renamed or discontinued after the order.

**Integration (the strong answer):** existing orders already lost their original prices in memory.
`ProductCatalog.priceHistory()` records every change with a timestamp, so you can **backfill** each
line with the price in effect at `placedAt`. Cross-check against the processor's captured amount,
then re-run the affected reports.

---

## SUP-107: No confirmation email; error page after being charged

**Root cause, two layers:**

1. [`OrderAuditListener.java:24`](../shipfast/src/main/java/com/shipfast/audit/OrderAuditListener.java#L24)
   assumes any positive discount came from a coupon and calls `event.couponCode().toUpperCase()`.
   But **bulk pricing** (`BULK5`, 10+ units of one SKU) gives a discount with **no coupon**, which
   causes an NPE. That's why it's "mostly B2B accounts, not every order": only bulk orders without
   a coupon.
2. [`EventBus.publish`](../shipfast/src/main/java/com/shipfast/events/EventBus.java#L32) delivers
   synchronously with **no isolation**. The audit handler is subscribed first
   (`ShipFastApp.java:88-90`), so its exception stops loyalty and email from running. It also
   propagates out of `placeOrder` **after the card was charged and the order saved as PAID**. The
   controller maps it to HTTP 500, the customer retries, and now there's a second order.

The sim's error tracker shows the NPE message, so candidates who run the sim get there fast.

**Reproduction tests:** `T7_Events.bulkOrderWithoutCouponIsConfirmed` (through the controller: 201,
email, audit entry, loyalty points) and `failingHandlerDoesNotStopOthers`.

**Tempting fixes that mask it:**
- **A null check in the audit listener only.** That fixes today's NPE, but the next bug in *any*
  handler causes the same outage. The audit entry also silently loses the promo info.
- **Reordering the subscriptions** so email runs first. Customers still see a 500, loyalty is
  still skipped, and the code comment says audit goes first on purpose.
- Catching and ignoring the exception in `placeOrder`. The customer gets a 201, but every handler
  after the failing one still doesn't run.

**Correct fix:** both of these.
- Fix the NPE properly. Record `event.promotions()`, or the coupon code only if present.
- Isolate handler failures in `EventBus`: catch per handler, log or record a metric, and keep
  going. **Side effects after commit must never fail a committed operation.**

**Deeper follow-up:** an outbox pattern or async delivery with retries, so a failed email is
retried rather than lost. With at-least-once delivery, handlers must be **idempotent**. Loyalty
would double-award on a retry. Strong candidates spot that.

**Edge cases:** bulk + coupon (never broke), coupon only, no discount, a handler throwing an `Error`
vs. a `RuntimeException`, and the fact that handler order is no longer significant.

**Integration:**
- Backfill: PAID orders with no `ORDER_PLACED` audit entry are exactly the affected ones. Resend
  confirmations and credit the loyalty points.
- Contact customers with duplicate retry orders and offer refunds.
- Monitor handler failures, and alert on "500 after capture" (the sim's "charged but shown an
  error" check).

---

## SUP-108: Fully refunded orders stuck as PARTIALLY_REFUNDED

**Root cause:**
[`Money.java:117`](../shipfast/src/main/java/com/shipfast/common/Money.java#L117) uses
`BigDecimal.equals`, which **compares scale**: `25.5 != 25.50` and `80 != 80.00`.

- The admin UI posts amounts as JS numbers, so `25.50` becomes `"25.5"` (see the sim's
  `asTypedInAdminUi`).
- `Money.ZERO` has scale 0, so the running `refunded` total keeps the input's scale.
- [`RefundService.java:49`](../shipfast/src/main/java/com/shipfast/refund/RefundService.java#L49)
  `getRefunded().equals(getTotal())` returns false, so the status becomes PARTIALLY_REFUNDED.
- The arithmetic uses `compareTo`, so the refundable balance is `$0.00`. Hence "nothing left to
  refund, but not refunded."

The "round-number orders" clue: totals ending in 0 cents (`25.50` becomes `"25.5"`, `80.00` becomes
`"80"`). `Money.toString()` always prints 2 decimals, so the failing assertion reads
`expected: <$25.50> but was: <$25.50>`. That confusing message is itself the tell.

**Reproduction tests:** `T8_Money.fullRefundTypedWithoutTrailingZerosMarksRefunded`
(`refund(id, "80")`) and `equalAmountsAreEqualRegardlessOfScale`.

**Tempting fixes that mask it:**
- `compareTo` or `setScale(2)` **only in `RefundService`**. That fixes one call site, but every
  other `Money.equals` (maps, sets, future code) stays broken.
- `setScale(2)` only in `SupportController`. That fixes one entry point.
- Making the admin UI send `toFixed(2)`. That's a frontend patch over a backend landmine.
- **Changing `equals` to `compareTo` without changing `hashCode`.** That breaks the equals/hashCode
  contract: equal `Money` values land in different `HashSet`/`HashMap` buckets. AI agents do this
  often, and it's a great catch. Test: `new HashSet<>(List.of(Money.of("0"), Money.of("0.00"),
  Money.ZERO)).size() == 1`.

**Correct fix (either works; the trade-off is the discussion):**
1. `equals` via `compareTo == 0` **and** `hashCode` via `amount.stripTrailingZeros().hashCode()`.
   (This is what the reference patch does. `stripTrailingZeros` on zero is safe on JDK 8+.)
2. Normalize the scale in the constructor with `setScale(2, HALF_EVEN)`. That changes rounding
   semantics globally, since sub-cent intermediate values get rounded. Run the full suite and think
   about sub-cent unit prices.

**Edge cases:** `"0"` vs `"0.00"`, negative amounts, `"1E+1"`, and **sub-cent input**: a support
agent typing `"25.505"`. Should the API reject more than 2 decimals? That's a product and payments
decision.

**Integration:** a one-off repair that flips orders where refunded equals total (by `compareTo`) and
status is PARTIALLY_REFUNDED to REFUNDED. Then grep for other `.equals(` on `Money`.

---

## Appendix: using the sim as the "how do you know it's correct" answer

After a fix, a strong candidate runs something like:

```powershell
foreach ($s in 1..10) { .\sim.ps1 --seed $s | Select-String "FAIL|checks failed" }
```

They then point out which line turned `[ OK ]`, **and that no other line turned red**, which rules
out a regression. They also say that thread timing makes a single run weak evidence, which is why
they ran several seeds.

To see the finished state: `patch -p1 < interviewer/reference-fix.patch` from `FakeInterview/`,
then run `sim`. It should show `0 of 11 checks failed`. Reset afterwards with
`git reset --hard` inside `shipfast/`.
