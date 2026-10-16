package com.investops.auth.controller;

import com.investops.auth.dto.LoginRequest;
import com.investops.auth.dto.LoginResponse;
import com.investops.auth.dto.UserDto;
import com.investops.auth.service.AuthService;
import com.investops.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.authenticate(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDto>> getCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated() || "anonymousUser".equals(authentication.getPrincipal())) {
            return ResponseEntity.ok(ApiResponse.ok(null));
        }
        UserDto user = authService.getUserByUsername(authentication.getName());
        return ResponseEntity.ok(ApiResponse.ok(user));
    }

    @GetMapping("/users")
    public ResponseEntity<ApiResponse<List<UserDto>>> listUsers() {
        List<UserDto> users = authService.listActiveUsers();
        return ResponseEntity.ok(ApiResponse.ok(users));
    }
}
