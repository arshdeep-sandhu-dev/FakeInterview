package com.shipfast.audit;

import java.time.Clock;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Append-only record of business-significant actions, retained for compliance. */
public class AuditLog {

    private final List<AuditEntry> entries = new CopyOnWriteArrayList<>();
    private final Clock clock;

    public AuditLog(Clock clock) {
        this.clock = clock;
    }

    public void record(String action, String subjectId, String detail) {
        entries.add(new AuditEntry(clock.instant(), action, subjectId, detail));
    }

    public List<AuditEntry> entries() {
        return List.copyOf(entries);
    }

    public List<AuditEntry> entriesFor(String subjectId) {
        return entries.stream()
                .filter(entry -> entry.subjectId().equals(subjectId))
                .toList();
    }

    public List<AuditEntry> entriesWithAction(String action) {
        return entries.stream()
                .filter(entry -> entry.action().equals(action))
                .toList();
    }
}
