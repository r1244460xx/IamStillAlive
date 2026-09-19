package com.imsa.backend.dto;

import com.imsa.backend.entity.enums.Gender;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserUpdateRequest {

    private String email;

    private String nationalId;

    @NotBlank(message = "暱稱不能為空")
    private String nickname;

    @NotNull(message = "性別不能為空")
    private Gender gender;

    @NotNull(message = "生日不能為空")
    @Past(message = "生日必須是過去的時間")
    private LocalDate birthdate;
}
