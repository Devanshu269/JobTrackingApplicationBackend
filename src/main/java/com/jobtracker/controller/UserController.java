package com.jobtracker.controller;

import com.jobtracker.dto.UpdateDefaultResumeRequestDto;
import com.jobtracker.dto.UpdateProfileRequestDto;
import com.jobtracker.dto.UserDto;
import com.jobtracker.model.User;
import com.jobtracker.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PutMapping("/me")
    public ResponseEntity<UserDto> updateProfile(Authentication authentication,
                                                 @Valid @RequestBody UpdateProfileRequestDto dto) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateProfile(user, dto));
    }

    @PutMapping("/me/default-resume")
    public ResponseEntity<UserDto> updateDefaultResume(Authentication authentication,
                                                       @Valid @RequestBody UpdateDefaultResumeRequestDto dto) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(userService.updateDefaultResume(user, dto));
    }

    @DeleteMapping("/me/default-resume")
    public ResponseEntity<UserDto> clearDefaultResume(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(userService.clearDefaultResume(user));
    }
}
