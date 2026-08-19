package com.chatpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@EnableJpaAuditing
@SpringBootApplication
public class ChatPaymentSaasApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatPaymentSaasApplication.class, args);
    }
}
