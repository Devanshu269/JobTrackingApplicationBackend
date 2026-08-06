package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AuthResponseDto {

    private String token;

    private String refreshToken;

    private Integer userId;
}
