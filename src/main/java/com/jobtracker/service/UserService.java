package com.jobtracker.service;

import com.jobtracker.Utils.AuthUtils;
import com.jobtracker.dto.UpdateDefaultResumeRequestDto;
import com.jobtracker.dto.UpdateNotificationPreferencesDto;
import com.jobtracker.dto.UpdateProfileRequestDto;
import com.jobtracker.dto.UserDto;
import com.jobtracker.model.User;
import com.jobtracker.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final AuthUtils authUtils;

    public UserDto updateProfile(User user, UpdateProfileRequestDto dto) {
        if (dto.getFirstName() != null) {
            user.setUserFirstName(dto.getFirstName());
        }
        if (dto.getLastName() != null) {
            user.setUserLastName(dto.getLastName());
        }
        if (dto.getAvatarUrl() != null) {
            user.setAvatarUrl(dto.getAvatarUrl());
        }
        return authUtils.toUserDto(userRepository.save(user));
    }

    /** Partial: null means "leave unchanged", same convention as the job PATCH. */
    public UserDto updateNotificationPreferences(User user, UpdateNotificationPreferencesDto dto) {
        if (dto.getEmailNotifications() != null) {
            user.setEmailNotifications(dto.getEmailNotifications());
        }
        return authUtils.toUserDto(userRepository.save(user));
    }

    public UserDto updateDefaultResume(User user, UpdateDefaultResumeRequestDto dto) {
        user.setDefaultResumeUrl(dto.getResumeUrl());
        return authUtils.toUserDto(userRepository.save(user));
    }

    public UserDto clearDefaultResume(User user) {
        user.setDefaultResumeUrl(null);
        return authUtils.toUserDto(userRepository.save(user));
    }
}
