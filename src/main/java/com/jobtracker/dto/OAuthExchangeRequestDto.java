package com.jobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthExchangeRequestDto {

    @NotBlank(message = "Code is required")
    private String code;
}