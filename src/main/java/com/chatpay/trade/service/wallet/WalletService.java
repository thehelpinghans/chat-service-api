package com.chatpay.trade.service.wallet;

import com.chatpay.common.domain.User;
import com.chatpay.trade.domain.Trade;
import com.chatpay.trade.domain.Wallet;
import com.chatpay.trade.domain.WalletTransaction;
import com.chatpay.trade.repository.WalletRepository;
import com.chatpay.trade.repository.WalletTransactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class WalletService {

    private final WalletRepository walletRepository;
    private final WalletTransactionRepository walletTransactionRepository;

    @Transactional(readOnly = true)
    public Optional<Wallet> findWalletById(Long userId) {
        return walletRepository.findById(userId);
    }

    @Transactional
    public Wallet createWallet(User user) {
        return walletRepository.save(Wallet.create(user));
    }

    @Transactional
    public void debit(Wallet wallet, long amount) {
        wallet.pay(amount);
    }

    @Transactional
    public void recordPayment(Wallet wallet, Trade trade, long amount) {
        // flushを省略すると、wallet/tradeのUPDATE(dirty checking由来)はコミット時まで遅延し、INSERTより後で実行される。
        // INSERTは先に共有ロックを取得するため、同時決済時は双方のトランザクションが排他ロックへの切替待ちとなり、デッドロックが発生する。
        walletRepository.flush();
        walletTransactionRepository.save(WalletTransaction.createPayment(wallet, trade, amount));
    }
}
