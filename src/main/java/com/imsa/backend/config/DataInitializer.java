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

import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
    public static final String TEST_USER_EMERGENCY = "0909280630";

    @Override
    @Transactional
    public void run(String... args) {
        Optional<User> existingUserOpt = userRepository.findById(TEST_USER_ID);
        LocalDateTime now = LocalDateTime.now();
        User targetUser;

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

            targetUser = userRepository.save(demoUser);
            log.info("🌱 [DataInitializer] 測試帳號不存在，已自動建立：ID {}, 手機 {}, 暱稱 {}", 
                    targetUser.getId(), targetUser.getPhone(), targetUser.getNickname());
        } else {
            // 帳號存在：比對並校準帳號、密碼雜湊與啟用狀態，確保符合共識
            targetUser = existingUserOpt.get();
            boolean modified = false;

            if (!TEST_USER_PHONE.equals(targetUser.getPhone())) {
                targetUser.setPhone(TEST_USER_PHONE);
                modified = true;
            }

            if (!TEST_USER_NICKNAME.equals(targetUser.getNickname())) {
                targetUser.setNickname(TEST_USER_NICKNAME);
                modified = true;
            }

            if (!TEST_USER_EMERGENCY.equals(targetUser.getEmergencyContactPhone())) {
                targetUser.setEmergencyContactPhone(TEST_USER_EMERGENCY);
                modified = true;
            }

            // 檢查密碼是否依然符合預設密碼
            if (!passwordEncoder.matches(TEST_USER_PASSWORD, targetUser.getPasswordHash())) {
                targetUser.setPasswordHash(passwordEncoder.encode(TEST_USER_PASSWORD));
                modified = true;
                log.info("🔑 [DataInitializer] 測試帳號密碼與預設不符，已校準重設為預設密碼");
            }

            // 確保帳號處於啟用狀態，避免測試受阻
            if (targetUser.getStatus() != UserStatus.ACTIVE) {
                targetUser.setStatus(UserStatus.ACTIVE);
                modified = true;
            }

            if (modified) {
                targetUser = userRepository.save(targetUser);
                log.info("🔄 [DataInitializer] 測試帳號資料已完成校準同步 (手機: {}, 狀態: ACTIVE)", targetUser.getPhone());
            } else {
                log.info("✅ [DataInitializer] 測試帳號已存在且帳密完全符合共識 (ID: {}, 手機: {})", 
                        targetUser.getId(), targetUser.getPhone());
            }
        }

        // ==========================================
        // 初始化打卡紀錄：僅在完全無任何打卡紀錄時寫入第一筆初始紀錄
        // 避免生產或容器重啟時沖掉長者的真實打卡歷史
        // ==========================================
        List<LoginRecord> records = loginRecordRepository.findByUserId(targetUser.getId());
        if (records.isEmpty()) {
            LoginRecord initialRecord = LoginRecord.builder()
                    .user(targetUser)
                    .loginTime(now)
                    .deviceInfo("系統初始化")
                    .networkType("SystemInit")
                    .remark("系統初始化預設測試帳號")
                    .build();
            loginRecordRepository.save(initialRecord);
            log.info("🌱 [DataInitializer] 測試帳號打卡紀錄為空，已建立「系統初始化」第一筆紀錄 (時間: {})", now);
        } else {
            log.info("ℹ️ [DataInitializer] 測試帳號已有 {} 筆打卡紀錄，保留既有歷史不予清除", records.size());
        }
    }
}
