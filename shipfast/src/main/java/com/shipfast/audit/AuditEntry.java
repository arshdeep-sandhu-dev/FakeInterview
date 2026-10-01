package com.shipfast.audit;

import java.time.Instant;

public record AuditEntry(Instant at, String action, String subjectId, String detail) {
}
