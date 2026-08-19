package com.chatpay.trade.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.TenantId;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class WalletTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @TenantId
    @Column(nullable = false)
    private Long tenantId;

    @Column(nullable = false)
    private Long amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType type;

    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "wallet_id", nullable = false)
    private Wallet wallet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "trade_id", nullable = false)
    private Trade trade;

    // 호출부는 항상 양수(절대값)로 넘김 — 부호는 여기서 고정하므로 타입-부호 조합이 어긋날 수 없음
    public static WalletTransaction createPayment(Wallet wallet, Trade trade, Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("WalletTransaction amount는 양수(절대값)로 전달해야 함: amount=" + amount);
        }
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.wallet = wallet;
        walletTransaction.trade = trade;
        walletTransaction.amount = -amount;
        walletTransaction.type = TransactionType.PAYMENT;
        return walletTransaction;
    }

    public static WalletTransaction createRefund(Wallet wallet, Trade trade, Long amount) {
        if (amount == null || amount <= 0) {
            throw new IllegalArgumentException("WalletTransaction amount는 양수(절대값)로 전달해야 함: amount=" + amount);
        }
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.wallet = wallet;
        walletTransaction.trade = trade;
        walletTransaction.amount = amount;
        walletTransaction.type = TransactionType.REFUND;
        return walletTransaction;
    }
}
