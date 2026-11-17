package com.example.ai.agent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.ai.dto.ChatCommandRequest;
import com.example.ai.dto.ChatCommandResponse;
import com.example.ai.service.ChatService;
import com.fasterxml.jackson.databind.JsonNode;
import com.meg.agentsdk.prompt.PromptService;
import com.meg.agentsdk.prompt.dto.PromptResponse;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple2;

/**
 * Uses the actual typed agent, model protocol and MCP client; all HTTP endpoints are local doubles.
 */
class ReferenceFlowTest {
    @Test
    void usesRequestReferenceAndForwardsModelArguments() throws Exception {
        try (HttpBackend backend = new HttpBackend()) {
            PromptService prompts = spy(new PromptService(null, null, null, "test", "main"));
            doReturn(
                            new PromptResponse("master", "v1", "SOP-ALPHA"),
                            new PromptResponse("master", "v2", "SOP-BETA"),
                            new PromptResponse("master", "v3", "SOP-GAMMA"))
                    .when(prompts)
                    .latest("ddod", "master");
            new ApplicationContextRunner()
                    .withBean(PromptService.class, () -> prompts)
                    .withUserConfiguration(GstOdAgent.class, ChatService.class)
                    .withBean(
                            com.meg.agentsdk.prompt.repository.PromptVersionRepository.class,
                            () -> mock(com.meg.agentsdk.prompt.repository.PromptVersionRepository.class))
                    .withBean(
                            com.meg.agentsdk.prompt.cache.PromptCacheService.class,
                            () -> mock(com.meg.agentsdk.prompt.cache.PromptCacheService.class))
                    .withBean(
                            com.meg.agentsdk.prompt.lakefs.LakeFSService.class,
                            () -> mock(com.meg.agentsdk.prompt.lakefs.LakeFSService.class))
                    .withPropertyValues(
                            "lakefs.endpoint=http://localhost:1", "lakefs.access-key=test", "lakefs.secret-key=test")
                    .withPropertyValues(
                            "agent.model=test-model",
                            "agent.gateway.base-url=" + backend.base() + "/v1",
                            "agent.gateway.api-key=test",
                            "agent.gateway.mcp-url=" + backend.base() + "/mcp",
                            "lakefs.path.workflow=ddod",
                            "lakefs.path.prompt=master",
                            "react-agent.permissions.allow[0]=processWorkflowCommand",
                            "react-agent.permissions.allow[1]=verify_otp",
                            "react-agent.prompts.enabled=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(backend.initializations.get()).isEqualTo(1);
                        verify(prompts, never()).latest("ddod", "master");
                        ChatService service = context.getBean(ChatService.class);
                        Tuple2<ChatCommandResponse, ChatCommandResponse> results = Mono.zip(
                                        service.chat(new ChatCommandRequest(
                                                "11", "session123", "REQUEST-ID", "first status")),
                                        service.chat(new ChatCommandRequest(
                                                "22", "session456", "OTHER-ID", "second status")))
                                .block(Duration.ofSeconds(45));
                        ChatCommandResponse result = results.getT1();
                        verify(prompts, times(2)).latest("ddod", "master");
                        String firstSop = sopFor(backend, "first status");
                        String secondSop = sopFor(backend, "second status");
                        assertThat(firstSop).isNotEqualTo(secondSop);
                        assertThat(result.answer()).contains("status:REQUEST-ID");
                        assertThat(results.getT2().answer()).contains("status:OTHER-ID");
                        assertMcpArguments(backend, "first status", "REQUEST-ID");
                        assertMcpArguments(backend, "second status", "OTHER-ID");
                        service.chat(new ChatCommandRequest("33", "session789", "IGNORED", "no-reference"))
                                .block(Duration.ofSeconds(45));
                        assertThat(backend.mcpRequests.getLast().path("name").asText())
                                .isEqualTo("verify_otp");
                        assertThat(backend.mcpRequests
                                        .getLast()
                                        .path("arguments")
                                        .size())
                                .isZero();
                        assertThat(result.sessionId()).isEqualTo("session123");
                        assertThat(results.getT2().sessionId()).isEqualTo("session456");
                        assertThat(backend.initializations.get()).isEqualTo(1);
                        assertThat(backend.toolCalls.get()).isEqualTo(3);
                        assertThat(backend.errors).isEmpty();
                    });
        }
    }

    private static String sopFor(HttpBackend backend, String query) {
        java.util.List<String> requests = backend.modelRequests.stream()
                .filter(body -> body.contains(query))
                .toList();
        assertThat(requests).isNotEmpty();
        String sop = requests.getFirst().contains("SOP-ALPHA") ? "SOP-ALPHA" : "SOP-BETA";
        String other = sop.equals("SOP-ALPHA") ? "SOP-BETA" : "SOP-ALPHA";
        assertThat(requests).allSatisfy(body -> assertThat(body).contains(sop).doesNotContain(other));
        return sop;
    }

    private static void assertMcpArguments(HttpBackend backend, String query, String referenceId) {
        JsonNode metadata = backend.mcpRequests.stream()
                .map(call -> call.path("arguments").path("body").path("metadata"))
                .filter(value -> value.path("requestId").asText().equals("req-" + query))
                .findFirst()
                .orElseThrow();
        assertThat(metadata.path("referenceId").asText()).isEqualTo(referenceId);
        assertThat(metadata.path("idempotencyKey").asText()).isEqualTo("idem-" + query);
        assertThat(metadata.path("operation").asText()).isEqualTo("GET_STATE");
    }
}
