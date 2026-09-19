package com.imsa.backend.service;

import com.imsa.backend.dto.EmergencyContactRequest;
import com.imsa.backend.dto.EmergencyContactResponse;
import com.imsa.backend.dto.UserRegisterRequest;
import com.imsa.backend.dto.UserResponse;
import com.imsa.backend.entity.User;
import com.imsa.backend.entity.enums.Gender;
import com.imsa.backend.repository.EmergencyContactRepository;
import com.imsa.backend.repository.LoginRecordRepository;
import com.imsa.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:testdb3;DB_CLOSE_DELAY=-1;MODE=PostgreSQL",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class EmergencyContactServiceTest {

    @Autowired
    private EmergencyContactService emergencyContactService;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmergencyContactRepository emergencyContactRepository;

    @Autowired
    private LoginRecordRepository loginRecordRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        emergencyContactRepository.deleteAll();
        loginRecordRepository.deleteAll();
        userRepository.deleteAll();

        // 透過 registerUser 建立使用者，並驗證自動關聯第一筆聯絡人
        UserRegisterRequest regReq = UserRegisterRequest.builder()
                .phone("0912345678")
                .password("password123")
                .nickname("小明")
                .gender(Gender.MALE)
                .birthdate(LocalDate.of(1995, 1, 1))
                .emergencyContactName("媽媽")
                .emergencyContactPhone("0987654321")
                .build();

        UserResponse userResp = userService.registerUser(regReq);
        assertNotNull(userResp);
        assertEquals(1, userResp.getEmergencyContacts().size());
        assertEquals("媽媽", userResp.getEmergencyContacts().get(0).getName());
        assertEquals("0987654321", userResp.getEmergencyContacts().get(0).getPhone());

        testUser = userRepository.findById(userResp.getId()).orElseThrow();
    }

    @Test
    @DisplayName("驗證查詢緊急聯絡人清單")
    void testGetContacts() {
        List<EmergencyContactResponse> list = emergencyContactService.getContacts(testUser.getId());
        assertEquals(1, list.size());
        assertEquals("媽媽", list.get(0).getName());
        assertEquals("0987654321", list.get(0).getPhone());
    }

    @Test
    @DisplayName("驗證新增第二位緊急聯絡人")
    void testAddContactSuccess() {
        EmergencyContactRequest req = EmergencyContactRequest.builder()
                .name("爸爸")
                .phone("0911223344")
                .build();

        EmergencyContactResponse response = emergencyContactService.addContact(testUser.getId(), req);
        assertNotNull(response.getId());
        assertEquals("爸爸", response.getName());
        assertEquals("0911223344", response.getPhone());

        List<EmergencyContactResponse> list = emergencyContactService.getContacts(testUser.getId());
        assertEquals(2, list.size());
    }

    @Test
    @DisplayName("驗證不可新增本人手機號碼為緊急聯絡人")
    void testAddContactWithSelfPhoneFails() {
        EmergencyContactRequest req = EmergencyContactRequest.builder()
                .name("自己")
                .phone("0912345678") // 本人電話
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            emergencyContactService.addContact(testUser.getId(), req);
        });
        assertTrue(ex.getMessage().contains("本人之手機號碼"));
    }

    @Test
    @DisplayName("驗證不可重複新增相同手機號碼")
    void testAddDuplicatePhoneFails() {
        EmergencyContactRequest req = EmergencyContactRequest.builder()
                .name("媽媽2")
                .phone("0987654321") // 與第 1 筆重複
                .build();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            emergencyContactService.addContact(testUser.getId(), req);
        });
        assertTrue(ex.getMessage().contains("已存在"));
    }

    @Test
    @DisplayName("驗證修改緊急聯絡人姓名與電話")
    void testUpdateContactSuccess() {
        List<EmergencyContactResponse> list = emergencyContactService.getContacts(testUser.getId());
        UUID contactId = list.get(0).getId();

        EmergencyContactRequest req = EmergencyContactRequest.builder()
                .name("母親大人")
                .phone("0988000111")
                .build();

        EmergencyContactResponse updated = emergencyContactService.updateContact(testUser.getId(), contactId, req);
        assertEquals("母親大人", updated.getName());
        assertEquals("0988000111", updated.getPhone());
    }

    @Test
    @DisplayName("核心防呆：當只有一位聯絡人時，刪除應被拒絕並拋出例外")
    void testDeleteLastContactFails() {
        List<EmergencyContactResponse> list = emergencyContactService.getContacts(testUser.getId());
        assertEquals(1, list.size());
        UUID contactId = list.get(0).getId();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> {
            emergencyContactService.deleteContact(testUser.getId(), contactId);
        });
        assertTrue(ex.getMessage().contains("至少需保留一位緊急聯絡人"));
    }

    @Test
    @DisplayName("驗證有多位聯絡人時，能正常刪除其中一位")
    void testDeleteContactSuccessWhenMultiple() {
        EmergencyContactRequest req = EmergencyContactRequest.builder()
                .name("好友")
                .phone("0933445566")
                .build();
        EmergencyContactResponse added = emergencyContactService.addContact(testUser.getId(), req);
        assertEquals(2, emergencyContactService.getContacts(testUser.getId()).size());

        // 刪除剛新增的這一位
        emergencyContactService.deleteContact(testUser.getId(), added.getId());

        List<EmergencyContactResponse> remaining = emergencyContactService.getContacts(testUser.getId());
        assertEquals(1, remaining.size());
        assertEquals("媽媽", remaining.get(0).getName());
    }
}
