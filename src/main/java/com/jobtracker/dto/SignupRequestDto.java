package com.jobtracker.dto;

import jakarta.validation.constraints.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignupRequestDto {

    @NotBlank(message = "First Name cannot be blank")
    @Size(min = 3, max = 20, message = "First name must be between 3 and 20 characters")
    private String userFirstName;

    @NotBlank(message = "Last Name cannot be blank")
    @Size(min = 3, max = 20, message = "Last name must be between 3 and 20 characters")
    private String userLastName;

    @NotBlank(message = "Password cannot be blank")
    @Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters")
    private String password;

    @Email(message = "Invalid email format")
    @NotBlank(message = "Email is required")
    private String email;

}
