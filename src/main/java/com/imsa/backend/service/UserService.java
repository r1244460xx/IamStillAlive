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

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));
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

    /**
     * 語意化一鍵打卡 API 業務邏輯
     */
    @Transactional
    public UserCheckInResponse checkIn(UUID userId, UserCheckInRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        LocalDateTime now = LocalDateTime.now();
        user.setLastActiveAt(now);

        // 如果先前處於警報狀態，自動解除並重置回 SAFE
        if (user.getSafetyStatus() == SafetyStatus.ALERTED) {
            user.setSafetyStatus(SafetyStatus.SAFE);
            log.info("💚 使用者 [{}] 完成一鍵打卡，安全狀態已由 ALERTED 重置為 SAFE！", user.getNickname());
        }
        userRepository.save(user);

        LoginRecord record = LoginRecord.builder()
                .user(user)
                .loginTime(now)
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
                .checkInTime(now)
                .safetyStatus(SafetyStatus.SAFE)
                .nextCheckInDeadline(now.plusHours(24))
                .message("打卡成功！已為您更新安全狀態，祝您平安順心。")
                .build();
    }
}
