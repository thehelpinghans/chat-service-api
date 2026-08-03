package com.chatpay.saas.domain;

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

    public static WalletTransaction create(Wallet wallet, Trade trade, Long amount, TransactionType type) {
        validateSign(amount, type);
        WalletTransaction walletTransaction = new WalletTransaction();
        walletTransaction.wallet = wallet;
        walletTransaction.trade = trade;
        walletTransaction.amount = amount;
        walletTransaction.type = type;
        return walletTransaction;
    }

    private static void validateSign(Long amount, TransactionType type) {
        // TransactionType.PAYMENT > Wallet.balance 차감 → amount < 0
        if (type == TransactionType.PAYMENT && amount >= 0) {
            throw new IllegalArgumentException(
                    "WalletTransaction 부호 불변식 위반: type=PAYMENT, amount=" + amount + " (amount < 0 이어야 함)");
        }
        // TransactionType.REFUND > Wallet.balance 증가 → amount > 0
        if (type == TransactionType.REFUND && amount <= 0) {
            throw new IllegalArgumentException(
                    "WalletTransaction 부호 불변식 위반: type=REFUND, amount=" + amount + " (amount > 0 이어야 함)");
        }
    }
}
