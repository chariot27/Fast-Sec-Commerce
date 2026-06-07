package com.fsc.secengine.audit.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AuditTransactionRepository extends JpaRepository<AuditTransaction, UUID> {
}
