package com.chatpay.integration.chat;

import com.chatpay.common.domain.Tenant;
import com.chatpay.common.domain.TenantStatus;
import com.chatpay.chat.dto.room.ChatRoomCreateRequest;
import com.chatpay.chat.dto.room.ChatRoomUpsertResult;
import com.chatpay.chat.repository.ChatRoomRepository;
import com.chatpay.chat.service.room.ChatRoomService;
import com.chatpay.common.repository.ItemRepository;
import com.chatpay.common.repository.TenantRepository;
import com.chatpay.common.repository.UserRepository;
import com.chatpay.trade.repository.WalletRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.ArrayList;
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

@SpringBootTest
class ChatRoomServiceIntegrationTest {

    private static final String TENANT_ATTRIBUTE = "CURRENT_TENANT_ID";

    @Autowired
    private ChatRoomService chatRoomService;

    @Autowired
    private TenantRepository tenantRepository;

    @Autowired
    private ChatRoomRepository chatRoomRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ItemRepository itemRepository;

    @Autowired
    private WalletRepository walletRepository;

    private record ConcurrentRunResult(List<ChatRoomUpsertResult> results, List<Throwable> errors) {}

    private ConcurrentRunResult runConcurrently(List<Supplier<ChatRoomUpsertResult>> tasks, Long tenantId, long timeoutSeconds) throws InterruptedException {
        CyclicBarrier barrier = new CyclicBarrier(tasks.size());
        ExecutorService executor = Executors.newFixedThreadPool(tasks.size());

        List<Callable<ChatRoomUpsertResult>> callables = tasks.stream()
                .<Callable<ChatRoomUpsertResult>>map(task -> () -> runWithTenantContext(tenantId, () -> {
                    barrier.await();
                    return task.get();
                }))
                .toList();
        List<Future<ChatRoomUpsertResult>> futures = executor.invokeAll(callables, timeoutSeconds, TimeUnit.SECONDS);
        executor.shutdown();

        List<ChatRoomUpsertResult> results = new ArrayList<>();
        List<Throwable> errors = new ArrayList<>();
        for (Future<ChatRoomUpsertResult> future : futures) {
            try {
                results.add(future.get());
            } catch (ExecutionException e) {
                errors.add(e.getCause());
            }
        }
        return new ConcurrentRunResult(results, errors);
    }

    // ワーカースレッドは実際のHTTPリクエストを通らずTenantFilterが効かないため、代わりに設定/クリアする
    private <T> T runWithTenantContext(Long tenantId, Callable<T> action) throws Exception {
        ServletRequestAttributes attributes = new ServletRequestAttributes(new MockHttpServletRequest());
        attributes.setAttribute(TENANT_ATTRIBUTE, tenantId, RequestAttributes.SCOPE_REQUEST);
        RequestContextHolder.setRequestAttributes(attributes);
        try {
            return action.call();
        } finally {
            RequestContextHolder.resetRequestAttributes();
        }
    }

    @Test
    @DisplayName("同時生成は1件のみ成功、再試行で確定")
    void concurrentChatRoomCreationOnlyOneSucceedsAndRetryConfirmsWinner() throws Exception {
        // given
        Tenant tenant = tenantRepository.save(Tenant.builder()
                .name("test1")
                .status(TenantStatus.ACTIVE)
                .webhookUrl("asdf1234").build());
        ChatRoomCreateRequest request = new ChatRoomCreateRequest("ext-user-1", "ext-item-1", "테스트 상품", 1000L);
        Supplier<ChatRoomUpsertResult> create = () -> chatRoomService.getOrCreateChatRoom(request);

        // when
        ConcurrentRunResult run = runConcurrently(List.of(create, create), tenant.getId(), 10);

        // then
        assertThat(run.results()).hasSize(1);
        assertThat(run.errors()).hasSize(1);
        assertThat(run.errors().getFirst()).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(run.results().getFirst().created()).isTrue();

        runWithTenantContext(tenant.getId(), () -> {
            assertThat(chatRoomRepository.findAll()).hasSize(1);
            assertThat(userRepository.findAll()).hasSize(1);
            assertThat(itemRepository.findAll()).hasSize(1);
            assertThat(walletRepository.findAll()).hasSize(1);
            return null;
        });

        ChatRoomUpsertResult retried = runWithTenantContext(tenant.getId(),
                () -> chatRoomService.getOrCreateChatRoom(request));
        assertThat(retried.created()).isFalse();
        assertThat(retried.body()).isEqualTo(run.results().getFirst().body());

        runWithTenantContext(tenant.getId(), () -> {
            assertThat(chatRoomRepository.findAll()).hasSize(1);
            assertThat(userRepository.findAll()).hasSize(1);
            assertThat(itemRepository.findAll()).hasSize(1);
            assertThat(walletRepository.findAll()).hasSize(1);
            return null;
        });
    }
}
