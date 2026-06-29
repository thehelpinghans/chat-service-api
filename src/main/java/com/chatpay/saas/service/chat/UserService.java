package com.chatpay.saas.service.chat;

import com.chatpay.saas.domain.User;
import com.chatpay.saas.domain.Wallet;
import com.chatpay.saas.repository.UserRepository;
import com.chatpay.saas.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public User getOrCreateUser(String externalUserId) {
        User user = userRepository.findByExternalId(externalUserId)
                .orElseGet(() -> userRepository.save(User.create(externalUserId)));

        // 나중에 필요하면 walletservice로 추상화
        // 현재는 @MapsId로 User의 PK를 그대로 공유하는 종속 엔티티이므로 같이 생성
        walletRepository.findById(user.getId())
                .orElseGet(() -> walletRepository.save(Wallet.create(user)));
        return user;
    }
}
