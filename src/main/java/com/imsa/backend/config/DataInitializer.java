package com.imsa.backend.config;

import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final PasswordEncoder passwordEncoder;

    public static final UUID TEST_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final String TEST_USER_PHONE = "0912345678";
    public static final String TEST_USER_PASSWORD = "pass123456";
    public static final String TEST_USER_NICKNAME = "測試者";
    public static final String TEST_USER_EMERGENCY = "0987654321";

    @Override
    public void run(String... args) {
        if (!userRepository.existsById(TEST_USER_ID)) {
            LocalDateTime now = LocalDateTime.now();
            User demoUser = User.builder()
                    .id(TEST_USER_ID)
                    .phone(TEST_USER_PHONE)
                    .passwordHash(passwordEncoder.encode(TEST_USER_PASSWORD))
                    .nickname(TEST_USER_NICKNAME)
                    .gender(Gender.OTHER)
                    .birthdate(LocalDate.of(1995, 1, 1))
                    .emergencyContactPhone(TEST_USER_EMERGENCY)
                    .status(UserStatus.ACTIVE)
                    .safetyStatus(SafetyStatus.SAFE)
                    .lastActiveAt(now)
                    .build();

            User saved = userRepository.save(demoUser);

            LoginRecord initialRecord = LoginRecord.builder()
                    .user(saved)
                    .loginTime(now)
                    .deviceInfo("系統初始化")
                    .networkType("SystemInit")
                    .remark("系統初始化預設測試帳號")
                    .build();
            loginRecordRepository.save(initialRecord);

            log.info("🌱 [DataInitializer] 已自動建立固定測試帳號：ID {}, 手機 {}, 暱稱 {}", 
                    saved.getId(), saved.getPhone(), saved.getNickname());
        }
    }
}
