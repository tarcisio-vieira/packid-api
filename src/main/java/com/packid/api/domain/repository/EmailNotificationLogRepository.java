package com.packid.api.domain.repository;

import com.packid.api.domain.model.EmailNotificationLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface EmailNotificationLogRepository extends JpaRepository<EmailNotificationLog, UUID> {
}
