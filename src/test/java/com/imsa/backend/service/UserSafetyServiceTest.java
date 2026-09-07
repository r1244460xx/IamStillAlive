package com.imsa.backend.service;

import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import com.imsa.backend.service.notification.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb2;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserSafetyServiceTest {

    @Autowired
    private UserSafetyService userSafetyService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginRecordRepository loginRecordRepository;

    @MockitoBean
    private NotificationService notificationService;

    private User overdueUser;

    @BeforeEach
    void setUp() {
        loginRecordRepository.deleteAll();
        userRepository.deleteAll();

        // 建立一位逾期 13 小時未打卡的使用者
        overdueUser = User.builder()
                .id(UUID.randomUUID())
                .phone("0912345678")
                .emergencyContactPhone("0909280630")
                .nickname("逾期守護對象")
                .passwordHash("hashed")
                .gender(Gender.MALE)
                .birthdate(LocalDate.of(1995, 5, 20))
                .status(UserStatus.ACTIVE)
                .safetyStatus(SafetyStatus.SAFE)
                .lastActiveAt(LocalDateTime.now().minusHours(13))
                .build();
        userRepository.save(overdueUser);

        when(notificationService.sendEmergencyAlert(anyString(), anyString())).thenReturn(true);
    }

    @Test
    @DisplayName("驗證當使用者超過 12 小時未打卡時，安全檢測能觸發 NotificationService 發送緊急通報並更新狀態為 ALERTED")
    void testCheckActiveUsersSafetyTriggersNotification() {
        userSafetyService.checkActiveUsersSafety();

        // 驗證 notificationService 有被呼叫，且收件人為緊急聯絡人電話
        verify(notificationService, times(1)).sendEmergencyAlert(
                eq("0909280630"),
                contains("逾期守護對象")
        );

        // 驗證使用者狀態被 CAS 更新為 ALERTED
        User updatedUser = userRepository.findById(overdueUser.getId()).orElseThrow();
        assertEquals(SafetyStatus.ALERTED, updatedUser.getSafetyStatus(), "逾期使用者狀態應被更新為 ALERTED");

        // 再次執行安全檢測，因狀態已為 ALERTED，不應重複發送通報（CAS 防護與冪等性）
        userSafetyService.checkActiveUsersSafety();
        verify(notificationService, times(1)).sendEmergencyAlert(anyString(), anyString());
    }
}
