package com.imsa.backend.repository;

import com.imsa.backend.entity.AlertDeliveryRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface AlertDeliveryRecordRepository extends JpaRepository<AlertDeliveryRecord, UUID> {
    List<AlertDeliveryRecord> findByAlertRecordId(UUID alertRecordId);
}
