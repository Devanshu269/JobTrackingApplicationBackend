package com.jobtracker.controller;

import com.jobtracker.dto.ActivityResponseDto;
import com.jobtracker.dto.PagedResponseDto;
import com.jobtracker.model.User;
import com.jobtracker.service.ActivityService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/activity")
@RequiredArgsConstructor
public class ActivityController {

    private final ActivityService activityService;

    /**
     * @param page zero-based, defaults to 0
     * @param size defaults to 20, clamped to 100. Clamped rather than rejected so an over-large
     *             value still returns useful data instead of a 400.
     */
    @GetMapping
    public ResponseEntity<PagedResponseDto<ActivityResponseDto>> listRecent(Authentication authentication,
                                                                            @RequestParam(required = false) Integer page,
                                                                            @RequestParam(required = false) Integer size) {
        User user = (User) authentication.getPrincipal();
        return ResponseEntity.ok(activityService.listRecent(user, page, size));
    }
}
