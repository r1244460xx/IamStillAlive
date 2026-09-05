package com.imsa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserEmergencyContactUpdateRequest {

    @NotBlank(message = "緊急聯絡人電話不能為空")
    @Pattern(regexp = "^09\\d{8}$", message = "緊急聯絡電話需為 09 開頭之 10 碼手機格式")
    private String emergencyContactPhone;
}