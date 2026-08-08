package com.jobtracker.Utils;

import com.jobtracker.dto.UserDto;
import com.jobtracker.enums.Provider;
import com.jobtracker.model.User;
import org.springframework.stereotype.Component;

@Component
public class AuthUtils {

    public User buildUser(String email, String firstName, String lastName, String avatarUrl,
                          Provider provider, boolean isActive) {
        User user = new User();
        user.setUserFirstName(firstName);
        user.setUserLastName(lastName);
        user.setEmail(email);
        user.setAvatarUrl(avatarUrl);
        user.setProvider(provider);
        user.setIsActive(isActive);
        return user;
    }

    public UserDto toUserDto(User user) {
        UserDto dto = new UserDto();
        dto.setUserId(user.getUserId());
        dto.setUserFirstName(user.getUserFirstName());
        dto.setUserLastName(user.getUserLastName());
        dto.setEmail(user.getEmail());
        dto.setAvatarUrl(user.getAvatarUrl());
        dto.setProvider(user.getProvider());
        return dto;
    }

}
