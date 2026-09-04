package com.imsa.backend.dto;

import com.imsa.backend.entity.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

@Data
public class UserRegisterRequest {

    @NotBlank(message = "電話號碼不能為空")
    @Pattern(regexp = "^09\\d{8}$", message = "電話號碼需為 09 開頭之 10 碼手機格式")
    private String phone;

    private String email;

    private String nationalId;

    @Pattern(regexp = "^$|^09\\d{8}$", message = "緊急聯絡電話需為 09 開頭之 10 碼手機格式")
    private String emergencyContactPhone;

    @NotBlank(message = "密碼不能為空")
    private String password;

    @NotBlank(message = "暱稱不能為空")
    private String nickname;

    @NotNull(message = "性別不能為空")
    private Gender gender;

    @NotNull(message = "生日不能為空")
    @Past(message = "生日必須是過去的時間")
    private LocalDate birthdate;
}
