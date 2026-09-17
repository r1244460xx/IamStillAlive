package com.imsa.backend.repository;

import com.imsa.backend.entity.AlertRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface AlertRecordRepository extends JpaRepository<AlertRecord, UUID> {

    List<AlertRecord> findByUserIdOrderByTriggerTimeDesc(UUID userId);

    Optional<AlertRecord> findFirstByUserIdOrderByTriggerTimeDesc(UUID userId);
}
