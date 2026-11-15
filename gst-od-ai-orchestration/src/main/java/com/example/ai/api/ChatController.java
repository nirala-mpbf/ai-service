package com.example.ai.api;

import com.example.ai.dto.ChatCommandRequest;
import com.example.ai.dto.ChatCommandResponse;
import com.example.ai.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {
    private final ChatService service;

    public ChatController(ChatService service) {
        this.service = service;
    }

    @PostMapping
    public Mono<ChatCommandResponse> chat(@Valid @RequestBody ChatCommandRequest request) {
        return service.chat(request);
    }
}
