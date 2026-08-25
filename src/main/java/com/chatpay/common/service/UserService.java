package com.chatpay.common.service;

import com.chatpay.common.domain.User;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.common.repository.UserRepository;
import com.chatpay.trade.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    @Transactional
    public User getOrCreateUser(String externalUserId) {
        Optional<User> existing = userRepository.findByExternalId(externalUserId);
        if (existing.isPresent()) {
            return existing.get();
        }

        User saved = userRepository.save(User.create(externalUserId));
        walletRepository.save(Wallet.create(userRepository.getReferenceById(saved.getId())));
        return saved;
    }

    public Optional<User> findUserByExternalId(String externalUserId) {
        return userRepository.findByExternalId(externalUserId);
    }
}
