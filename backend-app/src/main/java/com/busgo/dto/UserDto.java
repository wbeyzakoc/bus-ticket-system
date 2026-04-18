package com.busgo.dto;

import java.math.BigDecimal;

public record UserDto(
    String email, String username, String role, BigDecimal demoBalance, String companyName) {}
