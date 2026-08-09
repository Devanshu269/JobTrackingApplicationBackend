package com.jobtracker.dto;

import com.jobtracker.enums.Provider;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserDto {
    private Integer userId;
    private String userFirstName;
    private String userLastName;
    private String email;
    private String avatarUrl;
    private String defaultResumeUrl;
    private Provider provider;
}
