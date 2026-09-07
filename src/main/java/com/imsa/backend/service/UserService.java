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
import java.util.Optional;
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
        String cleanPhone = request.getPhone() != null ? request.getPhone().trim() : "";
        request.setPhone(cleanPhone);

        if (userRepository.existsByPhone(cleanPhone)) {
            throw new IllegalArgumentException("該電話號碼已註冊");
        }

        if (request.getEmergencyContactPhone() != null && !request.getEmergencyContactPhone().isBlank()) {
            String cleanEmergency = request.getEmergencyContactPhone().trim();
            request.setEmergencyContactPhone(cleanEmergency);
            if (cleanEmergency.equals(cleanPhone)) {
                throw new IllegalArgumentException("緊急聯絡人不可設定為本人之手機號碼");
            }
        }

        if (request.getNationalId() != null && !request.getNationalId().isBlank()) {
            request.setNationalId(request.getNationalId().trim());
        }

        LocalDateTime now = LocalDateTime.now();

        User user = User.builder()
                .phone(cleanPhone)
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
        String cleanPhone = request.getPhone() != null ? request.getPhone().trim() : "";

        User user = userRepository.findByPhone(cleanPhone)
                .orElseThrow(() -> new IllegalArgumentException("手機號碼或密碼錯誤"));

        // Case 9: 停權帳號檢查
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("🚫 停權使用者嘗試登入遭拒: {} (ID: {})", user.getPhone(), user.getId());
            throw new IllegalArgumentException("該帳號已被停權，無法使用此服務");
        }

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
            String cleanPhone = phone.trim();
            user = userRepository.findByPhone(cleanPhone).orElse(null);
            if (user != null) {
                log.info("🔄 getUserById: 透過手機門號 [{}] 自動找回對應使用者 (ID: {})", cleanPhone, user.getId());
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

        String cleanNationalId = (request.getNationalId() != null && !request.getNationalId().isBlank())
                ? request.getNationalId().trim()
                : request.getNationalId();

        user.setNickname(request.getNickname());
        user.setEmail(request.getEmail());
        user.setNationalId(cleanNationalId);
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
            String cleanPhone = phone.trim();
            user = userRepository.findByPhone(cleanPhone).orElse(null);
            if (user != null) {
                log.info("🔄 updateEmergencyContact: 透過手機門號 [{}] 自動找回對應使用者 (ID: {})", cleanPhone, user.getId());
            }
        }
        if (user == null) {
            throw new IllegalArgumentException("找不到該使用者");
        }

        if (emergencyContactPhone != null && !emergencyContactPhone.isBlank()) {
            String cleanEmergency = emergencyContactPhone.trim();
            if (cleanEmergency.equals(user.getPhone().trim())) {
                throw new IllegalArgumentException("緊急聯絡人不可設定為本人之手機號碼");
            }
            emergencyContactPhone = cleanEmergency;
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
            String cleanPhone = request.getPhone().trim();
            user = userRepository.findByPhone(cleanPhone).orElse(null);
            if (user != null) {
                log.info("🔄 CheckIn: 透過手機門號 [{}] 自動找回對應使用者 (ID: {})", cleanPhone, user.getId());
            }
        }
        if (user == null) {
            throw new IllegalArgumentException("找不到該使用者");
        }

        // Case 9: 停權帳號檢查
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("🚫 停權使用者嘗試打卡遭拒: {} (ID: {})", user.getPhone(), user.getId());
            throw new IllegalArgumentException("該帳號已被停權，無法進行打卡回報");
        }

        LocalDateTime now = LocalDateTime.now();

        // 冪等性防護 (Idempotency Key)：若客戶端提供 clientRequestId，先檢查是否已處理過
        String clientReqId = (request != null && request.getClientRequestId() != null && !request.getClientRequestId().isBlank())
                ? request.getClientRequestId().trim()
                : null;

        if (clientReqId != null) {
            Optional<LoginRecord> existingRecord = loginRecordRepository.findByClientRequestId(clientReqId);
            if (existingRecord.isPresent()) {
                LoginRecord existing = existingRecord.get();
                log.info("⏩ [打卡冪等命中] 收到重複打卡請求 (clientRequestId: {})，直接回傳既有打卡成功結果！", clientReqId);
                return UserCheckInResponse.builder()
                        .userId(user.getId())
                        .loginRecordId(existing.getId())
                        .checkInTime(existing.getLoginTime())
                        .safetyStatus(user.getSafetyStatus())
                        .nextCheckInDeadline(user.getLastActiveAt() != null ? user.getLastActiveAt().plusHours(24) : now.plusHours(24))
                        .message("打卡成功！(已同步最新安全狀態)")
                        .build();
            }
        }

        LocalDateTime eventTime = (request != null && request.getCheckInTime() != null) 
                ? request.getCheckInTime() 
                : now;

        // ⏰ 驗證客戶端時間是否異常超出未來時間 (容許 5 分鐘內的合理網路傳輸與鐘差)
        if (eventTime.isAfter(now.plusMinutes(5))) {
            log.warn("⚠️ [時鐘校正] 偵測到使用者 [{}] 之客戶端打卡時間處於未來時間 [{}]，已自動校正為伺服器時間 [{}]", 
                    user.getPhone(), eventTime, now);
            eventTime = now;
        }

        if (user.getLastActiveAt() == null || eventTime.isAfter(user.getLastActiveAt())) {
            user.setLastActiveAt(eventTime);
        }

        // Case 8: 判斷打卡事件是否具備「即時解除警報」效力
        // 只有當打卡事件時間落在最近 24 小時內，才能解除 ALERTED 警報狀態！
        boolean isRecentEvent = eventTime.isAfter(now.minusHours(24));
        String returnMsg = "打卡成功！已為您更新安全狀態，祝您平安順心。";

        if (user.getSafetyStatus() != SafetyStatus.SAFE) {
            if (isRecentEvent) {
                SafetyStatus oldStatus = user.getSafetyStatus();
                user.setSafetyStatus(SafetyStatus.SAFE);
                log.info("💚 使用者 [{}] 完成有效近期打卡，安全狀態已由 {} 重置為 SAFE！", user.getNickname(), oldStatus);
            } else {
                log.warn("⚠️ 使用者 [{}] 目前處於 ALERTED 警報狀態，但收到的補發打卡時間過於陳舊 ({}，超過 24 小時前)，不予解除警報！", 
                        user.getNickname(), eventTime);
                returnMsg = "打卡紀錄已保存，但因事件時間已超過 24 小時，目前仍維持警報狀態，請進行即時打卡解除警報。";
            }
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
                .clientRequestId(clientReqId)
                .build();

        LoginRecord savedRecord;
        try {
            savedRecord = loginRecordRepository.saveAndFlush(record);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            if (clientReqId != null) {
                log.info("⏩ [打卡併發冪等衝突] clientRequestId: {} 觸發唯一約束，回傳併發已寫入之紀錄", clientReqId);
                LoginRecord conflictRec = loginRecordRepository.findByClientRequestId(clientReqId).orElseThrow(() -> e);
                return UserCheckInResponse.builder()
                        .userId(user.getId())
                        .loginRecordId(conflictRec.getId())
                        .checkInTime(conflictRec.getLoginTime())
                        .safetyStatus(user.getSafetyStatus())
                        .nextCheckInDeadline(user.getLastActiveAt() != null ? user.getLastActiveAt().plusHours(24) : now.plusHours(24))
                        .message("打卡成功！(已同步最新安全狀態)")
                        .build();
            }
            throw e;
        }

        return UserCheckInResponse.builder()
                .userId(user.getId())
                .loginRecordId(savedRecord.getId())
                .checkInTime(eventTime)
                .safetyStatus(user.getSafetyStatus())
                .nextCheckInDeadline(user.getLastActiveAt() != null ? user.getLastActiveAt().plusHours(24) : now.plusHours(24))
                .message(returnMsg)
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
