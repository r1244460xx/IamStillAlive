package com.imsa.backend.repository;

import com.imsa.backend.entity.EmergencyContact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface EmergencyContactRepository extends JpaRepository<EmergencyContact, UUID> {
    List<EmergencyContact> findByUserIdOrderByCreatedAtAsc(UUID userId);
    Optional<EmergencyContact> findByUserIdAndPhone(UUID userId, String phone);
    long countByUserId(UUID userId);
}
