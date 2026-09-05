package com.imsa.backend.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {

    @NotBlank(message = "手機號碼為必填")
    private String phone;

    @NotBlank(message = "密碼為必填")
    private String password;
}
