package com.example.ai.service;

import com.example.ai.agent.GstOdAgent;
import com.example.ai.dto.ChatCommandRequest;
import com.example.ai.dto.ChatCommandResponse;
import io.agentscope.core.agent.RuntimeContext;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class ChatService {
    private final GstOdAgent agent;

    public ChatService(GstOdAgent agent) {
        this.agent = agent;
    }

    public Mono<ChatCommandResponse> chat(ChatCommandRequest request) {
        io.agentscope.core.agent.RuntimeContext.Builder contextBuilder =
                RuntimeContext.builder().userId(request.userId()).sessionId(request.sessionId());
        if (request.referenceId() != null) {
            contextBuilder.put("referenceId", request.referenceId());
        }
        RuntimeContext context = contextBuilder.build();
        return agent.askAsync(request.message(), context)
                .map(reply -> new ChatCommandResponse(request.sessionId(), reply.content()));
    }
}
