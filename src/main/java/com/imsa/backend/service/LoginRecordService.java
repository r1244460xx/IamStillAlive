package com.imsa.backend.service;

import com.imsa.backend.dto.LoginRecordResponse;
import com.imsa.backend.entity.LoginRecord;
import com.imsa.backend.entity.User;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class LoginRecordService {

    private final LoginRecordRepository loginRecordRepository;
    private final UserRepository userRepository;

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
    public void deleteRecord(UUID id) {
        if (!loginRecordRepository.existsById(id)) {
            throw new IllegalArgumentException("找不到該登入紀錄");
        }
        loginRecordRepository.deleteById(id);
    }
}
