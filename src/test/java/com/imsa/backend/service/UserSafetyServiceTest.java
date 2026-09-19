package com.imsa.backend.service;

import com.imsa.backend.entity.AlertDeliveryRecord;
import com.imsa.backend.entity.AlertRecord;
import com.imsa.backend.entity.EmergencyContact;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.AlertStatus;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.AlertDeliveryRecordRepository;
import com.imsa.backend.repository.AlertRecordRepository;
import com.imsa.backend.repository.EmergencyContactRepository;
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
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
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
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private LoginRecordRepository loginRecordRepository;

    @Autowired
    private AlertRecordRepository alertRecordRepository;

    @Autowired
    private AlertDeliveryRecordRepository alertDeliveryRecordRepository;

    @MockitoBean
    private NotificationService notificationService;

    private User overdueUser;

    @BeforeEach
    void setUp() {
        alertDeliveryRecordRepository.deleteAll();
        alertRecordRepository.deleteAll();
        emergencyContactRepository.deleteAll();
        loginRecordRepository.deleteAll();
        userRepository.deleteAll();

        // 建立一位逾期 13 小時未打卡的使用者
        overdueUser = User.builder()
                .id(UUID.randomUUID())
                .phone("0912345678")
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
    @DisplayName("驗證當使用者超過 12 小時未打卡時，安全檢測能對多位緊急聯絡人進行群發，並成功寫入主表與明細表")
    void testCheckActiveUsersSafetyBroadcastsToMultipleContacts() {
        // 新增 2 位緊急聯絡人
        EmergencyContact c1 = EmergencyContact.builder()
                .user(overdueUser)
                .name("父親")
                .phone("0909280630")
                .build();
        EmergencyContact c2 = EmergencyContact.builder()
                .user(overdueUser)
                .name("母親")
                .phone("0988111222")
                .build();
        emergencyContactRepository.saveAll(List.of(c1, c2));

        userSafetyService.checkActiveUsersSafety();

        // 驗證 notificationService 被呼叫兩次，分別發給兩位聯絡人
        verify(notificationService, times(1)).sendEmergencyAlert(eq("0909280630"), contains("逾期守護對象"));
        verify(notificationService, times(1)).sendEmergencyAlert(eq("0988111222"), contains("逾期守護對象"));

        // 驗證使用者狀態被 CAS 更新為 ALERTED
        User updatedUser = userRepository.findById(overdueUser.getId()).orElseThrow();
        assertEquals(SafetyStatus.ALERTED, updatedUser.getSafetyStatus());

        // 驗證主表 alert_records
        List<AlertRecord> records = alertRecordRepository.findAll();
        assertEquals(1, records.size(), "應有一筆主告警紀錄");
        AlertRecord record = records.get(0);
        assertEquals(AlertStatus.SMS_SENT, record.getStatus(), "全數成功時主表應為 SMS_SENT");
        assertEquals(2, record.getTotalContacts());
        assertEquals(2, record.getSuccessCount());
        assertEquals(0, record.getFailedCount());

        // 驗證明細表 alert_delivery_records
        List<AlertDeliveryRecord> deliveries = alertDeliveryRecordRepository.findAll();
        assertEquals(2, deliveries.size(), "應有 2 筆寄送明細");
        for (AlertDeliveryRecord d : deliveries) {
            assertEquals(AlertStatus.SMS_SENT, d.getStatus());
            assertNotNull(d.getSentAt());
        }

        // 再次執行安全檢測，因狀態已為 ALERTED，不應重複發送通報（CAS 防護與冪等性）
        userSafetyService.checkActiveUsersSafety();
        verify(notificationService, times(2)).sendEmergencyAlert(anyString(), anyString());
        assertEquals(1, alertRecordRepository.count(), "不應產生重複之告警紀錄");
    }

    @Test
    @DisplayName("驗證群發時單一聯絡人發送失敗/例外具備故障隔離，其餘聯絡人仍能成功收到簡訊，且主表標記為 PARTIALLY_SENT")
    void testFailureIsolationDuringBroadcast() {
        EmergencyContact c1 = EmergencyContact.builder()
                .user(overdueUser)
                .name("聯絡人A")
                .phone("0911111111")
                .build();
        EmergencyContact c2 = EmergencyContact.builder()
                .user(overdueUser)
                .name("聯絡人B")
                .phone("0922222222")
                .build();
        emergencyContactRepository.saveAll(List.of(c1, c2));

        // c1 拋出網路異常，c2 正常成功
        when(notificationService.sendEmergencyAlert(eq("0911111111"), anyString()))
                .thenThrow(new RuntimeException("Simulated Vonage Timeout"));
        when(notificationService.sendEmergencyAlert(eq("0922222222"), anyString()))
                .thenReturn(true);

        userSafetyService.checkActiveUsersSafety();

        // 驗證兩位聯絡人都有被嘗試發送（c1 拋例外沒有中斷對 c2 的發送！）
        verify(notificationService, times(1)).sendEmergencyAlert(eq("0911111111"), anyString());
        verify(notificationService, times(1)).sendEmergencyAlert(eq("0922222222"), anyString());

        // 主表狀態應為 PARTIALLY_SENT
        List<AlertRecord> records = alertRecordRepository.findAll();
        assertEquals(1, records.size());
        AlertRecord record = records.get(0);
        assertEquals(AlertStatus.PARTIALLY_SENT, record.getStatus());
        assertEquals(2, record.getTotalContacts());
        assertEquals(1, record.getSuccessCount());
        assertEquals(1, record.getFailedCount());

        // 明細表分別為 1 筆 SMS_FAILED 與 1 筆 SMS_SENT
        List<AlertDeliveryRecord> deliveries = alertDeliveryRecordRepository.findAll();
        assertEquals(2, deliveries.size());

        AlertDeliveryRecord d1 = deliveries.stream().filter(d -> d.getContactPhone().equals("0911111111")).findFirst().orElseThrow();
        assertEquals(AlertStatus.SMS_FAILED, d1.getStatus());
        assertTrue(d1.getErrorMessage().contains("Simulated Vonage Timeout"));

        AlertDeliveryRecord d2 = deliveries.stream().filter(d -> d.getContactPhone().equals("0922222222")).findFirst().orElseThrow();
        assertEquals(AlertStatus.SMS_SENT, d2.getStatus());
        assertNotNull(d2.getSentAt());
    }

    @Test
    @DisplayName("驗證當使用者未設定任何緊急聯絡人時，主表記錄為 NO_CONTACT_PHONE，不發送簡訊")
    void testCheckActiveUsersSafetyWithNoEmergencyContacts() {
        // overdueUser 沒有任何緊急聯絡人
        userSafetyService.checkActiveUsersSafety();

        verify(notificationService, never()).sendEmergencyAlert(anyString(), anyString());

        List<AlertRecord> records = alertRecordRepository.findAll();
        assertEquals(1, records.size());
        AlertRecord record = records.get(0);
        assertEquals(AlertStatus.NO_CONTACT_PHONE, record.getStatus());
        assertEquals(0, record.getTotalContacts());
    }
}
