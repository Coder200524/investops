package com.investops.auth.dto;

import com.investops.auth.entity.UserRole;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    private UUID userId;
    private String username;
    private String email;
    private UserRole role;
    private String token;
}
