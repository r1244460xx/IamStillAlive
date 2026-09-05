package com.imsa.backend.service;

import com.imsa.backend.dto.*;
import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;
    private final LoginRecordRepository loginRecordRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public UserResponse registerUser(UserRegisterRequest request) {
        if (userRepository.existsByPhone(request.getPhone())) {
            throw new IllegalArgumentException("該電話號碼已註冊");
        }

        if (request.getEmergencyContactPhone() != null && !request.getEmergencyContactPhone().isBlank()) {
            if (request.getEmergencyContactPhone().trim().equals(request.getPhone().trim())) {
                throw new IllegalArgumentException("緊急聯絡人不可設定為本人之手機號碼");
            }
        }

        if (request.getNationalId() != null && !request.getNationalId().isBlank()) {
            if (userRepository.existsByNationalId(request.getNationalId())) {
                throw new IllegalArgumentException("該身分證字號已被使用");
            }
        }

        LocalDateTime now = LocalDateTime.now();

        User user = User.builder()
                .phone(request.getPhone())
                .email(request.getEmail())
                .nationalId(request.getNationalId())
                .emergencyContactPhone(request.getEmergencyContactPhone())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .nickname(request.getNickname())
                .gender(request.getGender())
                .birthdate(request.getBirthdate())
                .status(UserStatus.ACTIVE)
                .safetyStatus(SafetyStatus.SAFE)
                .lastActiveAt(now)
                .build();

        User savedUser = userRepository.save(user);

        // 冷啟動：註冊成功時自動建立第一筆「初始活躍紀錄」
        LoginRecord initialRecord = LoginRecord.builder()
                .user(savedUser)
                .loginTime(now)
                .remark("帳號註冊 (初始打卡報平安)")
                .build();
        loginRecordRepository.save(initialRecord);

        log.info("🎉 新使用者註冊成功：{} (ID: {})，已建立初始打卡活躍紀錄。", savedUser.getNickname(), savedUser.getId());
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional
    public UserResponse login(UserLoginRequest request) {
        User user = userRepository.findByPhone(request.getPhone())
                .orElseThrow(() -> new IllegalArgumentException("手機號碼或密碼錯誤"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("手機號碼或密碼錯誤");
        }

        LocalDateTime now = LocalDateTime.now();
        user.setLastActiveAt(now);
        if (user.getSafetyStatus() != SafetyStatus.SAFE) {
            user.setSafetyStatus(SafetyStatus.SAFE);
        }
        User savedUser = userRepository.save(user);

        LoginRecord record = LoginRecord.builder()
                .user(savedUser)
                .loginTime(now)
                .remark("使用者手動登入")
                .build();
        loginRecordRepository.save(record);

        log.info("🔑 使用者登入成功：{} (ID: {})", savedUser.getNickname(), savedUser.getId());
        return UserResponse.fromEntity(savedUser);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        return getUserById(id, null);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id, String phone) {
        User user = null;
        if (id != null) {
            user = userRepository.findById(id).orElse(null);
        }
        if (user == null && phone != null && !phone.isBlank()) {
            user = userRepository.findByPhone(phone).orElse(null);
            if (user != null) {
                log.info("🔄 getUserById: 透過手機門號 [{}] 自動找回對應使用者 (ID: {})", phone, user.getId());
            }
        }
        if (user == null) {
            throw new IllegalArgumentException("找不到該使用者");
        }
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateUser(UUID id, UserUpdateRequest request) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        // 如果修改了身分證字號，且已被其他帳號使用，則不允許
        if (request.getNationalId() != null && !request.getNationalId().isBlank() 
                && !request.getNationalId().equals(user.getNationalId())) {
            if (userRepository.existsByNationalId(request.getNationalId())) {
                throw new IllegalArgumentException("該身分證字號已被其他使用者綁定");
            }
        }

        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setNationalId(request.getNationalId());
        user.setEmergencyContactPhone(request.getEmergencyContactPhone());
        user.setGender(request.getGender());
        user.setBirthdate(request.getBirthdate());

        User updatedUser = userRepository.save(user);
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional
    public void changePassword(UUID userId, UserPasswordChangeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("原密碼輸入錯誤，請重新確認");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
        log.info("🔑 使用者 [{}] 成功變更登入密碼", user.getPhone());
    }

    @Transactional
    public UserResponse updateEmergencyContact(UUID userId, String phone, String emergencyContactPhone) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && phone != null && !phone.isBlank()) {
            user = userRepository.findByPhone(phone).orElse(null);
            if (user != null) {
                log.info("🔄 updateEmergencyContact: 透過手機門號 [{}] 自動找回對應使用者 (ID: {})", phone, user.getId());
            }
        }
        if (user == null) {
            throw new IllegalArgumentException("找不到該使用者");
        }

        if (emergencyContactPhone != null && !emergencyContactPhone.isBlank()) {
            if (emergencyContactPhone.trim().equals(user.getPhone().trim())) {
                throw new IllegalArgumentException("緊急聯絡人不可設定為本人之手機號碼");
            }
        }

        user.setEmergencyContactPhone(emergencyContactPhone);
        User updated = userRepository.save(user);
        log.info("📞 使用者 [{}] 成功變更緊急聯絡人電話為 [{}]", user.getPhone(), emergencyContactPhone);
        return UserResponse.fromEntity(updated);
    }

    /**
     * 語意化一鍵打卡 API 業務邏輯
     */
    @Transactional
    public UserCheckInResponse checkIn(UUID userId, UserCheckInRequest request) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && request != null && request.getPhone() != null && !request.getPhone().isBlank()) {
            user = userRepository.findByPhone(request.getPhone()).orElse(null);
            if (user != null) {
                log.info("🔄 CheckIn: 透過手機門號 [{}] 自動找回對應使用者 (ID: {})", request.getPhone(), user.getId());
            }
        }
        if (user == null) {
            throw new IllegalArgumentException("找不到該使用者");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime eventTime = (request != null && request.getCheckInTime() != null) 
                ? request.getCheckInTime() 
                : now;

        // ⏰ Edge Case 3: 驗證客戶端時間是否異常超出未來時間 (容許 5 分鐘內的合理網路傳輸與鐘差)
        if (eventTime.isAfter(now.plusMinutes(5))) {
            log.warn("⚠️ [時鐘校正] 偵測到使用者 [{}] 之客戶端打卡時間處於未來時間 [{}]，已自動校正為伺服器時間 [{}]", 
                    user.getPhone(), eventTime, now);
            eventTime = now;
        }

        if (user.getLastActiveAt() == null || eventTime.isAfter(user.getLastActiveAt())) {
            user.setLastActiveAt(eventTime);
        }

        // 如果先前處於警報或預警狀態，自動解除並重置回 SAFE
        if (user.getSafetyStatus() != SafetyStatus.SAFE) {
            SafetyStatus oldStatus = user.getSafetyStatus();
            user.setSafetyStatus(SafetyStatus.SAFE);
            log.info("💚 使用者 [{}] 完成打卡，安全狀態已由 {} 重置為 SAFE！", user.getNickname(), oldStatus);
        }
        userRepository.save(user);

        LoginRecord record = LoginRecord.builder()
                .user(user)
                .loginTime(eventTime)
                .location(request != null ? request.getLocation() : null)
                .ipAddress(request != null ? request.getIpAddress() : null)
                .deviceInfo(request != null ? request.getDeviceInfo() : null)
                .networkType(request != null ? request.getNetworkType() : null)
                .remark(request != null && request.getRemark() != null && !request.getRemark().isBlank() 
                        ? request.getRemark() : "使用者一鍵打卡報平安")
                .build();

        LoginRecord savedRecord = loginRecordRepository.save(record);

        return UserCheckInResponse.builder()
                .userId(user.getId())
                .loginRecordId(savedRecord.getId())
                .checkInTime(eventTime)
                .safetyStatus(SafetyStatus.SAFE)
                .nextCheckInDeadline(eventTime.plusHours(24))
                .message("打卡成功！已為您更新安全狀態，祝您平安順心。")
                .build();
    }

    /**
     * 測試與驗證用：手動將指定使用者的最後活躍時間倒退特定小時數 (例如 25 小時)
     */
    @Transactional
    public UserResponse simulateOverdue(UUID userId, long hoursAgo) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        user.setLastActiveAt(LocalDateTime.now().minusHours(hoursAgo));
        user.setSafetyStatus(SafetyStatus.SAFE);
        User savedUser = userRepository.save(user);

        log.info("🧪 [測試模式] 已將使用者 [{}] 的最後活躍時間調回 {} 小時前: {}", 
                user.getNickname(), hoursAgo, user.getLastActiveAt());
        return UserResponse.fromEntity(savedUser);
    }
}
