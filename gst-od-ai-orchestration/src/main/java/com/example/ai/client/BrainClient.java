package com.example.ai.client;

import com.example.ai.config.MessagingProperties;
import com.example.ai.dto.BrainServiceResponse;
import java.net.URI;
import java.net.http.HttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Makes HTTP requests to Brain. */
@Component
@ConditionalOnProperty(prefix = "app.messaging", name = "enabled", havingValue = "true")
public class BrainClient {

    private static final Logger log = LoggerFactory.getLogger(BrainClient.class);

    private final RestClient restClient;

    public BrainClient(RestClient.Builder restClientBuilder, MessagingProperties properties) {
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.brain().connectTimeout())
                .build());
        requestFactory.setReadTimeout(properties.brain().readTimeout());
        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
    }

    public BrainServiceResponse post(URI endpoint, String action, Object payload, String correlationId) {
        log.info("Calling Brain. action={}, correlationId={}, endpoint={}", action, correlationId, endpoint.getPath());

        BrainServiceResponse response = restClient
                .post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-Correlation-Id", correlationId)
                .header("X-Action", action)
                .body(payload)
                .exchange((request, clientResponse) -> new BrainServiceResponse(
                        clientResponse.getStatusCode().value(),
                        clientResponse.getHeaders().getFirst(HttpHeaders.CONTENT_TYPE),
                        clientResponse.getBody().readAllBytes()));

        log.info(
                "Brain responded. action={}, correlationId={}, endpoint={}, status={}",
                action,
                correlationId,
                endpoint.getPath(),
                response.httpStatus());
        return response;
    }
}
