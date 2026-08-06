package com.jobtracker.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.Map;

@Getter
@Setter
public class ErrorResponseDto {
    private LocalDateTime timestamp;
    private int status;
    private String message;
    private Map<String, String> errors;
}
