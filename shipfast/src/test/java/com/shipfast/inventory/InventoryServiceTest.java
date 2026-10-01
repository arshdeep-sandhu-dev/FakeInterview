package com.shipfast.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class InventoryServiceTest {

    private final InventoryService inventory = new InventoryService(5);

    @Test
    void receivingStockMakesItAvailable() {
        inventory.receive("MUG", 10);
        inventory.receive("MUG", 5);
        assertEquals(15, inventory.available("MUG"));
        assertEquals(new StockSnapshot("MUG", 15, 15, 0, 0), inventory.snapshot("MUG"));
    }

    @Test
    void reservingMovesStockOffTheShelf() {
        inventory.receive("MUG", 10);
        assertTrue(inventory.reserve("MUG", 3));
        assertEquals(7, inventory.available("MUG"));
        assertEquals(3, inventory.reserved("MUG"));
    }

    @Test
    void cannotReserveMoreThanAvailable() {
        inventory.receive("MUG", 2);
        assertFalse(inventory.reserve("MUG", 3));
        assertEquals(2, inventory.available("MUG"));
        assertEquals(0, inventory.reserved("MUG"));
    }

    @Test
    void cannotReserveUnknownSku() {
        assertFalse(inventory.reserve("GHOST", 1));
    }

    @Test
    void releasingReturnsStockToTheShelf() {
        inventory.receive("MUG", 10);
        inventory.reserve("MUG", 4);
        inventory.release("MUG", 4);
        assertEquals(10, inventory.available("MUG"));
        assertEquals(0, inventory.reserved("MUG"));
    }

    @Test
    void committingMarksReservedStockAsSold() {
        inventory.receive("MUG", 10);
        inventory.reserve("MUG", 4);
        inventory.commit("MUG", 4);
        assertEquals(new StockSnapshot("MUG", 10, 6, 0, 4), inventory.snapshot("MUG"));
    }

    @Test
    void cannotReleaseOrCommitMoreThanReserved() {
        inventory.receive("MUG", 10);
        inventory.reserve("MUG", 2);
        assertThrows(IllegalStateException.class, () -> inventory.release("MUG", 3));
        assertThrows(IllegalStateException.class, () -> inventory.commit("MUG", 3));
    }

    @Test
    void notifiesWhenStockRunsLow() {
        List<String> alerts = new ArrayList<>();
        inventory.onLowStock((sku, remaining) -> alerts.add(sku + "=" + remaining));
        inventory.receive("MUG", 8);

        inventory.reserve("MUG", 2); // 6 left, above threshold
        inventory.reserve("MUG", 1); // 5 left, at threshold

        assertEquals(List.of("MUG=5"), alerts);
    }

    @Test
    void rejectsNonPositiveQuantities() {
        assertThrows(IllegalArgumentException.class, () -> inventory.receive("MUG", 0));
        assertThrows(IllegalArgumentException.class, () -> inventory.reserve("MUG", -1));
    }

    @Test
    void ledgerAlwaysAccountsForEveryUnit() {
        inventory.receive("MUG", 20);
        inventory.reserve("MUG", 5);
        inventory.reserve("MUG", 3);
        inventory.commit("MUG", 5);
        inventory.release("MUG", 3);
        StockSnapshot snapshot = inventory.snapshot("MUG");
        assertEquals(snapshot.received(), snapshot.accountedFor());
    }
}
