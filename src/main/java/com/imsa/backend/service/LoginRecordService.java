package com.imsa.backend.service;

import com.imsa.backend.dto.LoginRecordCreateRequest;
import com.imsa.backend.dto.LoginRecordResponse;
import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginRecordService {

    private final LoginRecordRepository loginRecordRepository;
    private final UserRepository userRepository;

    @Transactional
    public LoginRecordResponse createRecord(LoginRecordCreateRequest request) {
        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        // Case 9: 停權帳號檢查，防止已停權使用者透過通用打卡 API 重設安全狀態
        if (user.getStatus() == UserStatus.SUSPENDED) {
            log.warn("🚫 停權使用者嘗試建立打卡紀錄遭拒: {} (ID: {})", user.getPhone(), user.getId());
            throw new IllegalArgumentException("該帳號已被停權，無法新增打卡紀錄");
        }

        LocalDateTime now = LocalDateTime.now();

        LoginRecord record = LoginRecord.builder()
                .user(user)
                .loginTime(now)
                .location(request.getLocation())
                .ipAddress(request.getIpAddress())
                .deviceInfo(request.getDeviceInfo())
                .networkType(request.getNetworkType())
                .remark(request.getRemark())
                .build();

        LoginRecord savedRecord = loginRecordRepository.save(record);

        // 更新使用者的最後活躍時間
        user.setLastActiveAt(now);

        // 若使用者先前處於 ALERTED 警報或 WARNING 預警狀態，重新打卡後自動解除並重置為 SAFE
        if (user.getSafetyStatus() != SafetyStatus.SAFE) {
            user.setSafetyStatus(SafetyStatus.SAFE);
            log.info("💚 使用者 [{}] (ID: {}) 重新完成登入打卡，安全狀態已自動解除並重置為 SAFE！", 
                    user.getNickname(), user.getId());
        }
        userRepository.save(user);

        return LoginRecordResponse.fromEntity(savedRecord);
    }

    @Transactional(readOnly = true)
    public LoginRecordResponse getRecordById(UUID id) {
        LoginRecord record = loginRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到該登入紀錄"));
        return LoginRecordResponse.fromEntity(record);
    }

    @Transactional(readOnly = true)
    public List<LoginRecordResponse> getRecordsByUserId(UUID userId) {
        return getRecordsByUserId(userId, null);
    }

    @Transactional(readOnly = true)
    public List<LoginRecordResponse> getRecordsByUserId(UUID userId, String phone) {
        User user = null;
        if (userId != null) {
            user = userRepository.findById(userId).orElse(null);
        }
        if (user == null && phone != null && !phone.isBlank()) {
            user = userRepository.findByPhone(phone.trim()).orElse(null);
            if (user != null) {
                log.info("🔄 getRecordsByUserId: 透過手機門號 [{}] 自動找回使用者打卡紀錄 (ID: {})", phone.trim(), user.getId());
            }
        }
        if (user == null) {
            throw new IllegalArgumentException("找不到該使用者");
        }
        
        // Case 7: 每次僅撈出時間最近的 5 筆打卡紀錄，防止歷史紀錄無限膨脹導致伺服器 OOM
        List<LoginRecord> records = loginRecordRepository.findTop5ByUserIdOrderByLoginTimeDesc(user.getId());
        return records.stream()
                .map(LoginRecordResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public LoginRecordResponse updateRecord(UUID id, LoginRecordCreateRequest request) {
        LoginRecord record = loginRecordRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("找不到該登入紀錄"));

        // 更新可選欄位
        record.setLocation(request.getLocation());
        record.setIpAddress(request.getIpAddress());
        record.setDeviceInfo(request.getDeviceInfo());
        record.setNetworkType(request.getNetworkType());
        record.setRemark(request.getRemark());

        LoginRecord updatedRecord = loginRecordRepository.save(record);
        return LoginRecordResponse.fromEntity(updatedRecord);
    }

    @Transactional
    public void deleteRecord(UUID id) {
        if (!loginRecordRepository.existsById(id)) {
            throw new IllegalArgumentException("找不到該登入紀錄");
        }
        loginRecordRepository.deleteById(id);
    }
}
