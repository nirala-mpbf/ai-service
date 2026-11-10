package com.example.ai.agent;

import com.example.ai.api.ChatController.ChatResponse;
import com.meg.agentsdk.prompt.PromptService;
import com.meg.agentsdk.prompt.dto.PromptResponse;
import static org.mockito.Mockito.*;
import com.example.ai.api.ChatController.ChatRequest;
import com.example.ai.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import reactor.core.publisher.Mono;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Uses the actual typed agent, model protocol and MCP client; all HTTP endpoints are local doubles. */
class ReferenceFlowTest {
    @Test void usesRequestReferenceAndForwardsModelArguments() throws Exception {
        try (HttpBackend backend=new HttpBackend()) {
            PromptService prompts = mock(PromptService.class, CALLS_REAL_METHODS);
            doReturn(new PromptResponse("master", "v1", "SOP\nreferenceId: SOP-ID")).when(prompts).latest("ddod", "master");
            new ApplicationContextRunner().withBean(PromptService.class, () -> prompts).withUserConfiguration(GstOdAgent.class)
                    .withBean(com.meg.agentsdk.prompt.repository.PromptVersionRepository.class, () -> mock(com.meg.agentsdk.prompt.repository.PromptVersionRepository.class))
                    .withBean(com.meg.agentsdk.prompt.cache.PromptCacheService.class, () -> mock(com.meg.agentsdk.prompt.cache.PromptCacheService.class))
                    .withBean(com.meg.agentsdk.prompt.lakefs.LakeFSService.class, () -> mock(com.meg.agentsdk.prompt.lakefs.LakeFSService.class))
                    .withPropertyValues("lakefs.endpoint=http://localhost:1", "lakefs.access-key=test", "lakefs.secret-key=test")
                    .withPropertyValues("agent.model=test-model", "agent.gateway.base-url="+backend.base()+"/v1",
                            "agent.gateway.api-key=test", "agent.gateway.mcp-url="+backend.base()+"/mcp",
                            "react-agent.permissions.agents[gst-od-assistant].allow[0]=processWorkflowCommand", "lakefs.path.workflow=ddod", "lakefs.path.prompt=master", "react-agent.prompts.enabled=true", "react-agent.prompts.attach-reference-id=true")
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        ChatService service = new ChatService(context.getBean(GstOdAgent.class));
                        ChatResponse result = service.chat(new ChatRequest("11", "session123", "REQUEST-ID", "status")).block(Duration.ofSeconds(45));
                        verify(prompts).latest("ddod", "master");
                        assertThat(backend.modelRequests.toString()).contains("referenceId = REQUEST-ID").doesNotContain("referenceId = SOP-ID");
                        // The local model deliberately sends WRONG. The service forwards its tool arguments.
                        assertThat(result.answer()).contains("status:WRONG");
                        assertThat(result.sessionId()).isEqualTo("session123");
                        assertThat(backend.toolCalls.get()).isEqualTo(1);
                        assertThat(backend.errors).isEmpty();
                    });
        }
    }
}




