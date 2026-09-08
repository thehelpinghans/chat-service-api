package com.chatpay.common.service;

import com.chatpay.Fixture;
import com.chatpay.common.domain.User;
import com.chatpay.common.repository.UserRepository;
import com.chatpay.trade.service.wallet.WalletService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletService walletService;

    private UserService userService;

    private final User buyer = Fixture.createUser(1L, "buyer-1");

    private UserService newUserService() {
        return new UserService(userRepository, walletService);
    }

    @Test
    @DisplayName("既存ユーザーはそのまま返す")
    void returnsExistingUserWithoutCreatingWallet() {
        // given
        given(userRepository.findByExternalId("buyer-1")).willReturn(Optional.of(buyer));
        userService = newUserService();

        // when
        User result = userService.getOrCreateUser("buyer-1");

        // then
        assertThat(result).isEqualTo(buyer);
        verify(userRepository, never()).save(any());
        verifyNoInteractions(walletService);
    }

    @Test
    @DisplayName("新規ユーザー生成時にウォレットも作成")
    void createsNewUserAndWalletWhenNotFound() {
        // given
        given(userRepository.findByExternalId("buyer-1")).willReturn(Optional.empty());
        given(userRepository.save(any(User.class))).willAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 1L);
            return saved;
        });
        given(userRepository.getReferenceById(1L)).willReturn(buyer);
        userService = newUserService();

        // when
        User result = userService.getOrCreateUser("buyer-1");

        // then
        assertThat(result.getExternalId()).isEqualTo("buyer-1");
        verify(walletService).createWallet(buyer);
    }

    @Test
    @DisplayName("外部ID指定ユーザー取得")
    void findsUserByExternalId() {
        // given
        given(userRepository.findByExternalId("buyer-1")).willReturn(Optional.of(buyer));
        userService = newUserService();

        // when
        Optional<User> result = userService.findUserByExternalId("buyer-1");

        // then
        assertThat(result).contains(buyer);
    }

    @Test
    @DisplayName("ユーザー単件取得")
    void findsUserById() {
        // given
        given(userRepository.findById(1L)).willReturn(Optional.of(buyer));
        userService = newUserService();

        // when
        Optional<User> result = userService.findUserById(1L);

        // then
        assertThat(result).contains(buyer);
    }
}
