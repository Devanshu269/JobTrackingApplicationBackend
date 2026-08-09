package com.jobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ResetPasswordRequestDto {

    @NotBlank(message = "Token is required")
    private String token;

    @NotBlank(message = "New Password cannot be blank")
    @Size(min = 8, max = 64, message = "New Password must be between 8 and 64 characters")
    private String newPassword;
}
