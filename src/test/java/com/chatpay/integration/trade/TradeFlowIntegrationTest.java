package com.chatpay.integration.trade;

import com.chatpay.chat.domain.ChatMessage;
import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.chat.domain.MessageType;
import com.chatpay.chat.repository.ChatMessageRepository;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.common.domain.Item;
import com.chatpay.common.domain.User;
import com.chatpay.common.repository.ItemRepository;
import com.chatpay.common.service.UserService;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.TradeStatus;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.dto.PaymentResponse;
import com.chatpay.trade.dto.TradeCreateResponse;
import com.chatpay.trade.repository.TradeRepository;
import com.chatpay.trade.repository.WalletRepository;
import com.chatpay.trade.repository.WalletTransactionRepository;
import com.chatpay.trade.service.creation.TradeCreationService;
import com.chatpay.trade.service.payment.TradePaymentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// TradeCreationService(생성)와 TradePaymentService(결제)가 실제 빈으로 함께 엮여 만들어내는
// 전체 결제 흐름(생성→결제) 자체를 검증하는 시나리오 단위 통합테스트 — 유닛 경계와는 별개로 유지.
@SpringBootTest
class TradeFlowIntegrationTest {

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
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private WalletTransactionRepository walletTransactionRepository;

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
        Wallet wallet = walletRepository.findById(user.getId()).orElseThrow();
        ReflectionTestUtils.setField(wallet, "balance", balance);
        walletRepository.save(wallet);
    }

    private TradeCreateResponse.Created assertCreated(TradeCreateResponse response) {
        assertThat(response).isInstanceOf(TradeCreateResponse.Created.class);
        return (TradeCreateResponse.Created) response;
    }

    private PaymentResponse.PaymentSuccess assertSuccess(PaymentResponse response) {
        assertThat(response).isInstanceOf(PaymentResponse.PaymentSuccess.class);
        return (PaymentResponse.PaymentSuccess) response;
    }

    private record ConcurrentRunResult(List<PaymentResponse> results, List<Throwable> errors) {}

    private ConcurrentRunResult runPayTradeConcurrently(List<Supplier<PaymentResponse>> tasks, long timeoutSeconds) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());

        List<Callable<PaymentResponse>> callables = tasks.stream()
                .<Callable<PaymentResponse>>map(task -> () -> runWithTenantContext(() -> {
                    barrier.await();
                    return task.get();
                }))
                .toList();
        List<Future<PaymentResponse>> futures = executor.invokeAll(callables, timeoutSeconds, TimeUnit.SECONDS);
        executor.shutdown();

        List<PaymentResponse> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        for (Future<PaymentResponse> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        return new ConcurrentRunResult(results, errors);
    }

    private PaymentResponse runWithTenantContext(Callable<PaymentResponse> action) throws Exception {
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest());
        attributes.setAttribute(TENANT_ATTRIBUTE, SEED_TENANT_ID, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            return action.call();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    // スレッドでは実際のレースを確実に再現できないため(タイミング依存)、ユニーク制約違反を直接再現する
    @Test
    @DisplayName("PENDING取引の重複防止(ユニーク制約)")
    void rejectsDuplicatePendingTradeByUniqueConstraint() {
        // given
        ChatRoom chatRoom = createChatRoom("trade-test-buyer-constraint", "trade-test-item-constraint", "제약 검증 상품", 9000L);
        User buyer = chatRoom.getUser();
        Item item = chatRoom.getItem();
        ChatMessage firstMessage = chatMessageRepository.save(
                ChatMessage.create(chatRoom, null, "결제 요청 드립니다", MessageType.PAYMENT_REQUEST));
        tradeRepository.save(Trade.create(buyer, item, item.getName(), item.getPrice(), chatRoom, firstMessage));
        ChatMessage secondMessage = chatMessageRepository.save(
                ChatMessage.create(chatRoom, null, "결제 요청 드립니다", MessageType.PAYMENT_REQUEST));
        Trade secondPendingTrade = Trade.create(buyer, item, item.getName(), item.getPrice(), chatRoom, secondMessage);

        // when / then
        assertThatThrownBy(() -> tradeRepository.saveAndFlush(secondPendingTrade))
                .isInstanceOf(DataIntegrityViolationException.class);

        long pendingTradeCount = tradeRepository.findAll().stream()
                .filter(t -> t.getChatRoom().getId().equals(chatRoom.getId()))
                .filter(t -> t.getTradeStatus() == TradeStatus.PENDING)
                .count();
        assertThat(pendingTradeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("同一取引の同時決済は1件のみ成功")
    void concurrentPaymentOnSameTradeOnlyOneSucceeds() throws Exception {
        // given
        ChatRoom chatRoom = createChatRoom("pay-test-buyer-concurrency", "pay-test-item-concurrency", "동시결제 테스트 상품", 10000L);
        User buyer = chatRoom.getUser();
        createWalletWithBalance(buyer, 10000L);
        Long chatMessageId = assertCreated(tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null)).id();
        Supplier<PaymentResponse> pay = () -> tradePaymentService.payTrade(chatRoom.getId(), chatMessageId, chatRoom.getId(), buyer.getId());

        // when
        ConcurrentRunResult run = runPayTradeConcurrently(List.of(pay, pay), 10);

        // then
        assertThat(run.results()).hasSize(1);
        assertThat(run.errors()).hasSize(1);
        assertThat(run.errors().getFirst()).isInstanceOf(ObjectOptimisticLockingFailureException.class);
        PaymentResponse.PaymentSuccess concurrentSuccess = assertSuccess(run.results().getFirst());
        assertThat(concurrentSuccess.tradeStatus()).isEqualTo(TradeStatus.PAID);

        Wallet walletAfterConcurrentPayment = walletRepository.findById(buyer.getId()).orElseThrow();
        assertThat(walletAfterConcurrentPayment.getBalance()).isZero();

        Trade savedTrade = tradeRepository.findByChatMessageId(chatMessageId).orElseThrow();
        assertThat(savedTrade.getTradeStatus()).isEqualTo(TradeStatus.PAID);

        long walletTransactionCountForTrade = walletTransactionRepository.findAll().stream()
                .filter(wt -> wt.getTrade().getId().equals(savedTrade.getId()))
                .count();
        assertThat(walletTransactionCountForTrade).isEqualTo(1);
    }

    @Test
    @DisplayName("同一取引への多重決済攻撃でもデッドロックなし")
    void manyThreadsAttackingSameTradeCauseNoDeadlock() throws Exception {
        // given
        int attackerCount = 15;
        ChatRoom chatRoom = createChatRoom("attack-buyer-1", "attack-item-1", "동시공격 테스트 상품", 10000L);
        User buyer = chatRoom.getUser();
        createWalletWithBalance(buyer, 10000L);
        Long chatMessageId = assertCreated(tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null)).id();
        Supplier<PaymentResponse> pay = () -> tradePaymentService.payTrade(chatRoom.getId(), chatMessageId, chatRoom.getId(), buyer.getId());

        // when
        ConcurrentRunResult run = runPayTradeConcurrently(Collections.nCopies(attackerCount, pay), 30);

        // then
        assertThat(run.errors()).noneMatch(e -> e instanceof PessimisticLockingFailureException);

        long successCount = run.results().stream().filter(r -> r instanceof PaymentResponse.PaymentSuccess).count();
        long alreadyProcessedCount = run.results().stream().filter(r -> r instanceof PaymentResponse.PaymentAlreadyProcessed).count();
        long optimisticLockErrorCount = run.errors().stream()
                .filter(e -> e instanceof ObjectOptimisticLockingFailureException).count();

        assertThat(successCount).isEqualTo(1);
        assertThat(run.results().size() + run.errors().size()).isEqualTo(attackerCount);
        assertThat(alreadyProcessedCount + optimisticLockErrorCount).isEqualTo(attackerCount - 1);

        Wallet walletAfter = walletRepository.findById(buyer.getId()).orElseThrow();
        assertThat(walletAfter.getBalance()).isZero();

        Trade savedTrade = tradeRepository.findByChatMessageId(chatMessageId).orElseThrow();
        assertThat(savedTrade.getTradeStatus()).isEqualTo(TradeStatus.PAID);

        long walletTransactionCount = walletTransactionRepository.findAll().stream()
                .filter(wt -> wt.getTrade().getId().equals(savedTrade.getId()))
                .count();
        assertThat(walletTransactionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("同一ウォレットを共有する決済でもデッドロックなし")
    void concurrentPaymentsSharingWalletCauseNoDeadlock() throws Exception {
        // given
        ChatRoom chatRoomA = createChatRoom("attack-buyer-2", "attack-item-2a", "동시공격 테스트 상품A", 4000L);
        User buyer = chatRoomA.getUser();
        ChatRoom chatRoomB = createChatRoom("attack-buyer-2", "attack-item-2b", "동시공격 테스트 상품B", 6000L);
        createWalletWithBalance(buyer, 10000L);
        Long chatMessageIdA = assertCreated(tradeCreationService.createTrade(chatRoomA.getId(), chatRoomA.getId(), null)).id();
        Long chatMessageIdB = assertCreated(tradeCreationService.createTrade(chatRoomB.getId(), chatRoomB.getId(), null)).id();
        Supplier<PaymentResponse> payA = () -> tradePaymentService.payTrade(chatRoomA.getId(), chatMessageIdA, chatRoomA.getId(), buyer.getId());
        Supplier<PaymentResponse> payB = () -> tradePaymentService.payTrade(chatRoomB.getId(), chatMessageIdB, chatRoomB.getId(), buyer.getId());

        // when
        ConcurrentRunResult run = runPayTradeConcurrently(List.of(payA, payB), 30);

        // then
        assertThat(run.errors()).noneMatch(e -> e instanceof PessimisticLockingFailureException);
        assertThat(run.errors()).allMatch(e -> e instanceof ObjectOptimisticLockingFailureException);
        assertThat(run.results()).hasSize(1);
        assertThat(run.errors()).hasSize(1);
        assertThat(run.results().getFirst()).isInstanceOf(PaymentResponse.PaymentSuccess.class);

        Trade tradeA = tradeRepository.findByChatMessageId(chatMessageIdA).orElseThrow();
        Trade tradeB = tradeRepository.findByChatMessageId(chatMessageIdB).orElseThrow();
        boolean aPaid = tradeA.getTradeStatus() == TradeStatus.PAID;
        boolean bPaid = tradeB.getTradeStatus() == TradeStatus.PAID;
        assertThat(aPaid ^ bPaid).isTrue();
        assertThat(tradeA.getTradeStatus()).isIn(TradeStatus.PENDING, TradeStatus.PAID);
        assertThat(tradeB.getTradeStatus()).isIn(TradeStatus.PENDING, TradeStatus.PAID);

        Wallet walletAfter = walletRepository.findById(buyer.getId()).orElseThrow();
        assertThat(walletAfter.getBalance()).isIn(6000L, 4000L);

        long transactionCount = walletTransactionRepository.findAll().stream()
                .filter(wt -> wt.getTrade().getId().equals(tradeA.getId()) || wt.getTrade().getId().equals(tradeB.getId()))
                .count();
        assertThat(transactionCount).isEqualTo(1);
    }

    @Test
    @DisplayName("異なる購入者の同時決済は互いに干渉しない")
    void differentBuyersPayingConcurrentlyDoNotInterfere() throws Exception {
        // given
        int buyerCount = 8;
        List<User> buyers = new ArrayList<>();
        List<Supplier<PaymentResponse>> tasks = new ArrayList<>();
        for (int i = 0; i < buyerCount; i++) {
            ChatRoom chatRoom = createChatRoom("attack-buyer-multi-" + i, "attack-item-multi-" + i, "다중구매자 테스트 상품" + i, 1000L);
            User buyer = chatRoom.getUser();
            createWalletWithBalance(buyer, 5000L);
            buyers.add(buyer);
            Long chatMessageId = assertCreated(tradeCreationService.createTrade(chatRoom.getId(), chatRoom.getId(), null)).id();
            tasks.add(() -> tradePaymentService.payTrade(chatRoom.getId(), chatMessageId, chatRoom.getId(), buyer.getId()));
        }

        // when
        ConcurrentRunResult run = runPayTradeConcurrently(tasks, 30);

        // then
        assertThat(run.errors()).isEmpty();
        assertThat(run.results()).hasSize(buyerCount);
        assertThat(run.results()).allMatch(r -> r instanceof PaymentResponse.PaymentSuccess);
        for (User buyer : buyers) {
            Wallet wallet = walletRepository.findById(buyer.getId()).orElseThrow();
            assertThat(wallet.getBalance()).isEqualTo(4000L);
        }
    }
}
