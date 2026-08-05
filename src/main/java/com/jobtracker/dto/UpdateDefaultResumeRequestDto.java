package com.jobtracker.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateDefaultResumeRequestDto {

    @NotBlank(message = "Resume URL cannot be blank")
    private String resumeUrl;

}
