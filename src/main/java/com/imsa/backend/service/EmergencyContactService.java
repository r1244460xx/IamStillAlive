package com.imsa.backend.service;

import com.imsa.backend.dto.EmergencyContactRequest;
import com.imsa.backend.dto.EmergencyContactResponse;
import com.imsa.backend.entity.EmergencyContact;
import com.imsa.backend.entity.User;
import com.imsa.backend.repository.EmergencyContactRepository;
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
public class EmergencyContactService {

    private final EmergencyContactRepository emergencyContactRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<EmergencyContactResponse> getContacts(UUID userId) {
        return emergencyContactRepository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(EmergencyContactResponse::fromEntity)
                .collect(Collectors.toList());
    }

    @Transactional
    public EmergencyContactResponse addContact(UUID userId, EmergencyContactRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        String cleanPhone = request.getPhone().trim();
        String cleanName = request.getName().trim();

        if (cleanPhone.equals(user.getPhone().trim())) {
            throw new IllegalArgumentException("緊急聯絡人不可設定為本人之手機號碼");
        }

        if (emergencyContactRepository.findByUserIdAndPhone(userId, cleanPhone).isPresent()) {
            throw new IllegalArgumentException("該緊急聯絡人電話已存在");
        }

        EmergencyContact contact = EmergencyContact.builder()
                .user(user)
                .name(cleanName)
                .phone(cleanPhone)
                .build();

        EmergencyContact saved = emergencyContactRepository.save(contact);
        log.info("➕ 使用者 [{}] 新增緊急聯絡人: {} ({})", user.getPhone(), saved.getName(), saved.getPhone());
        return EmergencyContactResponse.fromEntity(saved);
    }

    @Transactional
    public EmergencyContactResponse updateContact(UUID userId, UUID contactId, EmergencyContactRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該使用者"));

        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該緊急聯絡人"));

        if (!contact.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權限修改該緊急聯絡人");
        }

        String cleanPhone = request.getPhone().trim();
        String cleanName = request.getName().trim();

        if (cleanPhone.equals(user.getPhone().trim())) {
            throw new IllegalArgumentException("緊急聯絡人不可設定為本人之手機號碼");
        }

        if (!cleanPhone.equals(contact.getPhone())) {
            if (emergencyContactRepository.findByUserIdAndPhone(userId, cleanPhone).isPresent()) {
                throw new IllegalArgumentException("該緊急聯絡人電話已存在");
            }
        }

        contact.setName(cleanName);
        contact.setPhone(cleanPhone);

        EmergencyContact saved = emergencyContactRepository.save(contact);
        log.info("✏️ 使用者 [{}] 更新緊急聯絡人: {} ({})", user.getPhone(), saved.getName(), saved.getPhone());
        return EmergencyContactResponse.fromEntity(saved);
    }

    @Transactional
    public void deleteContact(UUID userId, UUID contactId) {
        long count = emergencyContactRepository.countByUserId(userId);
        if (count <= 1) {
            throw new IllegalArgumentException("至少需保留一位緊急聯絡人，無法刪除");
        }

        EmergencyContact contact = emergencyContactRepository.findById(contactId)
                .orElseThrow(() -> new IllegalArgumentException("找不到該緊急聯絡人"));

        if (!contact.getUser().getId().equals(userId)) {
            throw new IllegalArgumentException("無權限刪除該緊急聯絡人");
        }

        emergencyContactRepository.delete(contact);
        log.info("🗑️ 使用者 [{}] 刪除緊急聯絡人: {} ({})", userId, contact.getName(), contact.getPhone());
    }
}
