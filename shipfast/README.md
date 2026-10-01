# ShipFast Checkout Service

Backend for the ShipFast storefront. It covers the catalog, pricing and promotions, inventory,
checkout, payments, refunds, notifications and finance reporting.

Persistence is in-memory in this repository. Everything else behaves like production, including
concurrency: the HTTP layer calls into these services from many threads at once.

## Requirements

- JDK 17+
- Nothing else. The test scripts download the JUnit console launcher into `lib/` if it is missing.
  A `pom.xml` is also provided if you prefer Maven.

## Build and test

```bash
./test.sh                                                  # macOS / Linux / Git Bash
.\test.ps1                                                 # Windows PowerShell

# a single class or method
./test.sh --select-class com.shipfast.order.OrderServiceTest
./test.sh --select-method com.shipfast.common.MoneyTest#addsAmounts

# with Maven instead
mvn test
```

## Load simulation

`sim` replays the Black Friday "Last Call" traffic spike against a fully wired app. That includes
real threads, a flaky payment processor, a limited-stock drop and a price change at midnight. It
then reconciles the store's records against independent sources of truth:

```bash
./sim.sh                      # random seed (printed at the top)
./sim.sh --seed 42            # repeatable traffic (thread timing still varies)
./sim.sh --threads 1          # same traffic, no concurrency
./sim.sh --shoppers 5000      # more load
```

The output includes HTTP response counts, the error tracker, both days' finance reports and a list
of reconciliation checks (`[ OK ]` / `[FAIL]`).

## Layout

| Package         | Responsibility                                                               |
|-----------------|------------------------------------------------------------------------------|
| `api`           | Controllers for the storefront (`CheckoutController`) and the support admin UI (`SupportController`) |
| `order`         | `OrderService` (checkout orchestration), `Order`, `OrderLine`, repository   |
| `pricing`       | `PricingEngine`, tax, shipping, bulk pricing, coupons                        |
| `inventory`     | Stock ledger: `available -> reserved -> sold`                                |
| `payment`       | `PaymentService` (retries), `PaymentGateway` client, sandbox processor       |
| `refund`        | Full and partial refunds                                                     |
| `events`        | In-process `EventBus` and domain events                                      |
| `notification`  | Email listeners and the outbox                                               |
| `audit`         | Compliance audit log                                                         |
| `customer`      | Customers and loyalty points                                                 |
| `reporting`     | Finance's daily sales report                                                 |
| `catalog`       | Products and list prices                                                     |
| `common`        | `Money`, ids, shared exceptions                                              |
| `sim`           | Load simulator and reconciliation (not deployed)                             |
| `ShipFastApp`   | Composition root that wires everything together                              |

## Checkout flow

```
CheckoutController.placeOrder
  -> OrderService.placeOrder
       1. load customer, build order lines (duplicate SKUs merged)
       2. validate coupon (if any), price cart (bulk pricing -> coupon -> tax -> shipping)
       3. reserve stock for every line
       4. charge card via PaymentService (retries gateway timeouts with backoff)
       5a. declined -> release stock, save order as PAYMENT_DECLINED
       5b. approved -> commit stock, redeem coupon, save order as PAID,
                       publish OrderPlacedEvent -> audit, loyalty points, confirmation email
```

## Conventions

- **Money** is always `com.shipfast.common.Money`, never `double`.
- **Time** comes from an injected `java.time.Clock`. Don't call `Instant.now()` directly.
- **Sleeping** (retry backoff) goes through `Sleeper`, so tests can skip it.
- The **store time zone** is `America/Los_Angeles`. Finance's "day" is a calendar day in that zone.
- `SandboxPaymentGateway` mirrors the real processor's documented API behaviour and is used in
  tests and the simulator.
