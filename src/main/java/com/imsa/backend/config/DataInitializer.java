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

@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        String testPhone = "0912345678";
        if (!userRepository.existsByPhone(testPhone)) {
            LocalDateTime now = LocalDateTime.now();
            User demoUser = User.builder()
                    .phone(testPhone)
                    .passwordHash(passwordEncoder.encode("pass123456"))
                    .nickname("測試者")
                    .gender(Gender.OTHER)
                    .birthdate(LocalDate.of(1995, 1, 1))
                    .emergencyContactPhone("0987654321")
                    .status(UserStatus.ACTIVE)
                    .safetyStatus(SafetyStatus.SAFE)
                    .lastActiveAt(now)
                    .build();

            User saved = userRepository.save(demoUser);

            LoginRecord initialRecord = LoginRecord.builder()
                    .user(saved)
                    .loginTime(now)
                    .remark("系統初始化預設測試帳號")
                    .build();
            loginRecordRepository.save(initialRecord);

            log.info("🌱 [DataInitializer] 已自動建立預設測試帳號：手機 {}, 暱稱 {}, ID {}", 
                    testPhone, saved.getNickname(), saved.getId());
        }
    }
}
