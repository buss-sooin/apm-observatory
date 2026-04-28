package com.apm.observatory.apiserver.auth.model;

public class AuthModel {

    public record LoginRequest(String username, String password) {}

    public record LoginResponse(String token) {}

    public record RegisterRequest(String username, String password) {}

}