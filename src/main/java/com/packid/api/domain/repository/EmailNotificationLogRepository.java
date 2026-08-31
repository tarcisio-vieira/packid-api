package com.packid.api.domain.repository;

import com.packid.api.domain.model.EmailNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EmailNotificationLogRepository extends JpaRepository<EmailNotificationLog, UUID> {

    @Query("""
            select distinct e.recipientEmail
              from EmailNotificationLog e
             where e.tenantId = :tenantId
               and e.changeType = :changeType
               and e.referenceKey = :referenceKey
               and e.status = 'SENT'
               and e.deleted = false
             order by e.recipientEmail
            """)
    List<String> findSentRecipientsByReference(
            @Param("tenantId") UUID tenantId,
            @Param("changeType") String changeType,
            @Param("referenceKey") String referenceKey
    );
}

