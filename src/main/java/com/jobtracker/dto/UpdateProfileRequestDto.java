package com.jobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateProfileRequestDto {

    @Size(min = 3, max = 20, message = "Username must be between 3 and 20 characters")
    private String name;

    private String avatarUrl;

}
