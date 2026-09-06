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
        java.util.Optional<User> existingUserOpt = userRepository.findById(TEST_USER_ID);
        LocalDateTime now = LocalDateTime.now();

        if (existingUserOpt.isEmpty()) {
            // 若被其他帳號佔用此手機號，先清理以確保唯一約束
            userRepository.findByPhone(TEST_USER_PHONE).ifPresent(conflictUser -> {
                log.warn("⚠️ [DataInitializer] 發現非固定 UUID 佔用測試手機號 {}，予以清理以確保測試帳號唯一", TEST_USER_PHONE);
                userRepository.delete(conflictUser);
            });

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

            log.info("🌱 [DataInitializer] 測試帳號不存在，已自動建立：ID {}, 手機 {}, 暱稱 {}", 
                    saved.getId(), saved.getPhone(), saved.getNickname());
        } else {
            // 帳號存在：比對並校準帳號、密碼雜湊與啟用狀態，確保符合共識
            User user = existingUserOpt.get();
            boolean modified = false;

            if (!TEST_USER_PHONE.equals(user.getPhone())) {
                user.setPhone(TEST_USER_PHONE);
                modified = true;
            }

            // 檢查密碼是否依然符合預設密碼
            if (!passwordEncoder.matches(TEST_USER_PASSWORD, user.getPasswordHash())) {
                user.setPasswordHash(passwordEncoder.encode(TEST_USER_PASSWORD));
                modified = true;
                log.info("🔑 [DataInitializer] 測試帳號密碼與預設不符，已校準重設為預設密碼");
            }

            // 確保帳號處於啟用狀態，避免測試受阻
            if (user.getStatus() != UserStatus.ACTIVE) {
                user.setStatus(UserStatus.ACTIVE);
                modified = true;
            }

            if (modified) {
                userRepository.save(user);
                log.info("🔄 [DataInitializer] 測試帳號資料已完成校準同步 (手機: {}, 狀態: ACTIVE)", user.getPhone());
            } else {
                log.info("✅ [DataInitializer] 測試帳號已存在且帳密完全符合共識 (ID: {}, 手機: {})", 
                        user.getId(), user.getPhone());
            }
        }
    }
}
