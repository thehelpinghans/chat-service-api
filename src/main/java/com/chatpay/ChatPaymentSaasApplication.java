package com.chatpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

// @EnableScheduling: TradeWebhookRetryScheduler(@Scheduled) 활성화용.
@EnableJpaAuditing
@EnableScheduling
@SpringBootApplication
public class ChatPaymentSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatPaymentSaasApplication.class, args);
    }
}
