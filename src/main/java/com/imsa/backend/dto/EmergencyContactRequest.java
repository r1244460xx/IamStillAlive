package com.imsa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class EmergencyContactRequest {

    @NotBlank(message = "聯絡人姓名不能為空")
    private String name;

    @NotBlank(message = "聯絡人電話不能為空")
    @Pattern(regexp = "^09\\d{8}$", message = "聯絡人電話需為 09 開頭之 10 碼手機格式")
    private String phone;
}
