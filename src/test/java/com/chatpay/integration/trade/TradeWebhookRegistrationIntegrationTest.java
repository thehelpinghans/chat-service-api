package com.chatpay.integration.trade;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.repository.ItemRepository;
import com.chatpay.common.service.UserService;
import com.chatpay.trade.domain.TradeWebhookRetry;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.repository.TradeWebhookRetryRepository;
import com.chatpay.trade.repository.WalletRepository;
import com.chatpay.trade.service.creation.TradeCreationService;
import com.chatpay.trade.service.payment.TradePaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

// outbox 패턴 검증 — payTrade 성공 시 TradeWebhookRetry row가 결제와 같은 트랜잭션으로,
// 백오프 없이 "즉시 마감"(nextAttemptAt=now) 상태로 등록되는지 확인. @Async가 삭제되어
// 스레드 경계를 넘는 전파 자체가 없어졌으므로, 폴링 없이 payTrade 리턴 직후 바로 검증 가능.
@SpringBootTest
class TradeWebhookRegistrationIntegrationTest {

    private static final String TENANT_ATTRIBUTE = "CURRENT_TENANT_ID";
    private static final Long SEED_TENANT_ID = 1L;

    @Autowired
    private TradeCreationService tradeCreationService;

    @Autowired
    private TradePaymentService tradePaymentService;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private TradeWebhookRetryRepository tradeWebhookRetryRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserService userService;

    @BeforeEach
    void setUpTenantContext() {
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest());
        attributes.setAttribute(TENANT_ATTRIBUTE, SEED_TENANT_ID, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);
    }

    @AfterEach
    void tearDownTenantContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    private ChatRoom createChatRoom(String externalUserId, String externalItemId, String itemName, long price) {
        User buyer = userService.getOrCreateUser(externalUserId);
        Item item = itemRepository.save(Item.create(externalItemId, itemName, price));
        return chatRoomRepository.save(ChatRoom.create(buyer, item));
    }

    private void createWalletWithBalance(User user, long balance) {
        var wallet = walletRepository.findById(user.getId()).orElseThrow();
        ReflectionTestUtils.setField(wallet, "balance", balance);
        walletRepository.save(wallet);
    }

    @Test
    @DisplayName("決済成功時にTradeWebhookRetryが即時マーク登録される")
    void registersWebhookRetryDueImmediatelyOnPaymentSuccess() {
        // given
        ChatRoom chatRoom = createChatRoom("webhook-reg-buyer", "webhook-reg-item", "outbox 등록 검증 상품", 5000L);
        User buyer = chatRoom.getUser();
        createWalletWithBalance(buyer, 5000L);

        TradeCreateResponse createResponse = tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null);
        assertThat(createResponse).isInstanceOf(TradeCreateResponse.Created.class);
        Long chatMessageId = ((TradeCreateResponse.Created) createResponse).id();

        // when — payTrade 트랜잭션 안에서 registerImmediateDelivery까지 같이 커밋됨
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC);
        PaymentResponse payResponse = tradePaymentService.payTrade(chatRoom.getId(), chatMessageId, chatRoom.getId(), buyer.getId());

        // then — 폴링 불필요, payTrade 리턴 즉시 row가 존재해야 함
        assertThat(payResponse).isInstanceOf(PaymentResponse.PaymentSuccess.class);
        Long tradeId = tradeRepository.findByChatMessageId(chatMessageId).orElseThrow().getId();

        TradeWebhookRetry retry = tradeWebhookRetryRepository.findById(tradeId).orElseThrow();
        assertThat(retry.getAttemptCount()).isEqualTo(1);
        assertThat(retry.getNextAttemptAt()).isBetween(before.minusSeconds(1), LocalDateTime.now(ZoneOffset.UTC));
    }
}
