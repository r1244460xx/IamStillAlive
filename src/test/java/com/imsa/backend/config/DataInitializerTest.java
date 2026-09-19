package com.imsa.backend.config;

import com.imsa.backend.entity.EmergencyContact;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.entity.enums.SafetyStatus;
import com.imsa.backend.entity.enums.UserStatus;
import com.imsa.backend.repository.EmergencyContactRepository;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb_init;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
@Transactional
class DataInitializerTest {

    @Autowired
    private DataInitializer dataInitializer;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private LoginRecordRepository loginRecordRepository;

    @Test
    @DisplayName("伺服器重啟/初始化時，測試帳號應清除所有非預設緊急聯絡人，僅保留唯一預設聯絡人 0909280630")
    void testInitializerRemovesExtraContactsExceptDefault() {
        // Given: 先準備測試帳號
        User user = userRepository.findById(DataInitializer.TEST_USER_ID).orElseGet(() -> {
            User newUser = User.builder()
                    .id(DataInitializer.TEST_USER_ID)
                    .phone(DataInitializer.TEST_USER_PHONE)
                    .passwordHash("hashed")
                    .nickname(DataInitializer.TEST_USER_NICKNAME)
                    .gender(Gender.OTHER)
                    .birthdate(LocalDate.of(1995, 1, 1))
                    .status(UserStatus.ACTIVE)
                    .safetyStatus(SafetyStatus.SAFE)
                    .lastActiveAt(LocalDateTime.now())
                    .build();
            return userRepository.save(newUser);
        });

        // 模擬測試過程中新增了額外的 2 筆緊急聯絡人
        EmergencyContact extra1 = EmergencyContact.builder()
                .user(user)
                .name("多餘聯絡人A")
                .phone("0988111222")
                .build();
        EmergencyContact extra2 = EmergencyContact.builder()
                .user(user)
                .name("多餘聯絡人B")
                .phone("0977333444")
                .build();
        emergencyContactRepository.saveAll(List.of(extra1, extra2));

        assertThat(emergencyContactRepository.findByUserIdOrderByCreatedAtAsc(user.getId())).hasSizeGreaterThanOrEqualTo(2);

        // When: 模擬伺服器啟動執行 DataInitializer
        dataInitializer.run();

        // Then: 檢查緊急聯絡人清單，應該只剩下唯一一筆預設聯絡人
        List<EmergencyContact> contactsAfter = emergencyContactRepository.findByUserIdOrderByCreatedAtAsc(user.getId());
        assertThat(contactsAfter).hasSize(1);
        assertThat(contactsAfter.get(0).getPhone()).isEqualTo(DataInitializer.TEST_USER_EMERGENCY);
        assertThat(contactsAfter.get(0).getName()).isEqualTo(DataInitializer.TEST_USER_EMERGENCY_NAME);
    }
}
