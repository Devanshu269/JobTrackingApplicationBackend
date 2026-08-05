package com.jobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequestDto {

    @NotBlank(message = "Current Password cannot be blank")
    @Size(min = 4, max = 12, message = "Current Password must be between 4 and 12 characters")
    private String currentPassword;

    @NotBlank(message = "New Password cannot be blank")
    @Size(min = 4, max = 12, message = "New Password must be between 4 and 12 characters")
    private String newPassword;
}
