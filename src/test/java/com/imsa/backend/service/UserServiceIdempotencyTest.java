package com.imsa.backend.service;

import com.imsa.backend.dto.UserCheckInRequest;
import com.imsa.backend.dto.UserCheckInResponse;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserServiceIdempotencyTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private LoginRecordRepository loginRecordRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        loginRecordRepository.deleteAll();
        userRepository.deleteAll();

        testUser = User.builder()
                .id(UUID.randomUUID())
                .phone("0911222333")
                .nickname("冪等測試員")
                .passwordHash("hashed")
                .gender(Gender.MALE)
                .birthdate(LocalDate.of(1990, 1, 1))
                .status(UserStatus.ACTIVE)
                .safetyStatus(SafetyStatus.SAFE)
                .lastActiveAt(LocalDateTime.now())
                .build();
        userRepository.save(testUser);
    }

    @Test
    @DisplayName("驗證打卡 API 具備客戶端請求 ID (clientRequestId) 冪等性防護")
    void testCheckInIdempotency() {
        String requestId = "req-" + UUID.randomUUID();

        UserCheckInRequest request1 = new UserCheckInRequest();
        request1.setPhone(testUser.getPhone());
        request1.setRemark("第一次打卡嘗試");
        request1.setClientRequestId(requestId);

        // 第一次呼叫打卡
        UserCheckInResponse response1 = userService.checkIn(testUser.getId(), request1);
        assertNotNull(response1);
        assertEquals(SafetyStatus.SAFE, response1.getSafetyStatus());
        assertEquals(1, loginRecordRepository.count(), "資料庫應恰好只有 1 筆打卡紀錄");

        // 模擬網路重試：送出具有相同 clientRequestId 的第二次打卡請求
        UserCheckInRequest request2 = new UserCheckInRequest();
        request2.setPhone(testUser.getPhone());
        request2.setRemark("網路重試之重複打卡");
        request2.setClientRequestId(requestId);

        UserCheckInResponse response2 = userService.checkIn(testUser.getId(), request2);
        assertNotNull(response2);
        // 驗證第二次回傳的是相同的既有紀錄 ID
        assertEquals(response1.getLoginRecordId(), response2.getLoginRecordId(), "兩次回傳的紀錄 ID 應相同");
        assertEquals(1, loginRecordRepository.count(), "資料庫依然嚴格維持 1 筆紀錄，未發生重複插入！");

        // 驗證換了新的 clientRequestId 後可以正常新增下一筆
        String newRequestId = "req-" + UUID.randomUUID();
        UserCheckInRequest request3 = new UserCheckInRequest();
        request3.setPhone(testUser.getPhone());
        request3.setRemark("下一次全新事件打卡");
        request3.setClientRequestId(newRequestId);

        UserCheckInResponse response3 = userService.checkIn(testUser.getId(), request3);
        assertNotNull(response3);
        assertNotEquals(response1.getLoginRecordId(), response3.getLoginRecordId());
        assertEquals(2, loginRecordRepository.count(), "新事件應成功產生第 2 筆紀錄");
    }
}
