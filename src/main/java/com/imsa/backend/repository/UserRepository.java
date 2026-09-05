package com.imsa.backend.repository;

import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByPhone(String phone);
    boolean existsByPhone(String phone);
    boolean existsByNationalId(String nationalId);
    List<User> findByStatus(UserStatus status);
    List<User> findByStatusAndSafetyStatusAndLastActiveAtBefore(
            UserStatus status, 
            SafetyStatus safetyStatus, 
            LocalDateTime threshold
    );
}
