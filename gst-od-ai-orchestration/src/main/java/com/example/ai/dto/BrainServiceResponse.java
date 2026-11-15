package com.example.ai.dto;

public record BrainServiceResponse(int httpStatus, String contentType, byte[] body) {}
