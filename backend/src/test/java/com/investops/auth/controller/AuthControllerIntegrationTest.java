package com.investops.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.investops.auth.dto.LoginRequest;
import com.investops.auth.entity.AppUser;
import com.investops.auth.entity.UserRole;
import com.investops.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController Integration Tests")
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        userRepository.save(AppUser.builder()
                .username("analyst1")
                .email("analyst1@investops.com")
                .passwordHash(passwordEncoder.encode("password123"))
                .role(UserRole.ANALYST)
                .active(true)
                .build());
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials returns 200 OK")
    void testLoginSuccess() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("analyst1")
                .password("password123")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("analyst1"))
                .andExpect(jsonPath("$.data.role").value("ANALYST"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with invalid password returns 401")
    void testLoginFailure() throws Exception {
        LoginRequest request = LoginRequest.builder()
                .username("analyst1")
                .password("wrongpassword")
                .build();

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/auth/users returns active users list")
    void testListUsers() throws Exception {
        mockMvc.perform(get("/api/v1/auth/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "analyst1", roles = "ANALYST")
    @DisplayName("GET /api/v1/auth/me returns current authenticated user")
    void testGetCurrentUser() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.username").value("analyst1"));
    }
}
