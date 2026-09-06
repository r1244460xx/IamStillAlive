package com.imsa.backend.repository;

import com.imsa.backend.entity.LoginRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LoginRecordRepository extends JpaRepository<LoginRecord, UUID> {
    List<LoginRecord> findByUserIdOrderByLoginTimeDesc(UUID userId);
    List<LoginRecord> findByUserId(UUID userId);
    Optional<LoginRecord> findFirstByUserIdOrderByLoginTimeDesc(UUID userId);
}
