package com.jobtracker.dto;

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
}
