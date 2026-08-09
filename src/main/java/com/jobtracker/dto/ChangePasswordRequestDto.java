package com.jobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequestDto {

    // Deliberately no @Size: this is an *existing* password being checked, not a new one being
    // set. Accounts created under the old 4-character minimum would otherwise fail validation
    // and be permanently unable to change their password.
    @NotBlank(message = "Current Password cannot be blank")
    private String currentPassword;

    @NotBlank(message = "New Password cannot be blank")
    @Size(min = 8, max = 64, message = "New Password must be between 8 and 64 characters")
    private String newPassword;
}
