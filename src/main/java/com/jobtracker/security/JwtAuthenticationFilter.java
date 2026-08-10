package com.jobtracker.security;

import com.jobtracker.model.User;
import com.jobtracker.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtUtil.validateToken(token)) {
                    String email = jwtUtil.extractEmail(token);
                    Optional<User> userOptional = userRepository.findByEmail(email);
                    // isActive is re-checked on every request, not just at login/refresh. A token
                    // issued before deactivation stays cryptographically valid for its full 15
                    // minutes, so without this a deactivated account keeps working until the token
                    // expires — and "deactivate this user" would not actually take effect.
                    if (userOptional.isPresent() && Boolean.TRUE.equals(userOptional.get().getIsActive())) {
                        User user = userOptional.get();
                        UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(user, null, Collections.emptyList());
                        SecurityContextHolder.getContext().setAuthentication(authToken);
                    }
                }
            }
        filterChain.doFilter(request, response);
    }
}
