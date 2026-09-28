package com.chatpay.trade.gateway;

import com.chatpay.trade.dto.WebhookPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class WebhookGateway {

    private final RestClient restClient;

    public WebhookGateway(RestClient.Builder restClientBuilder) {
        this.restClient = restClientBuilder.build();
    }

    public boolean send(String webhookUrl, WebhookPayload payload) {
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientException | IllegalArgumentException e) {
            // IllegalArgumentException: webhookUrl이 형식 위반이면 .uri()가 여기서 던짐(검증 없이 DB에 직접 들어가는 값)
            log.warn("Webhook delivery failed. tradeId={}", payload.tradeId(), e);
            return false;
        }
    }
}
