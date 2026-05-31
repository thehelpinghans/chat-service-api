package com.chatpay.saas.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Wallet extends BaseEntity {

    @Id
    private Long id;

    // @MapsId 가능: Wallet은 독립 식별자가 없고 User 1명당 반드시 1개 존재 → Wallet.id == User.id (PK 공유)
    @MapsId
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "id")
    private User user;

    @Version
    private Long version;

    @Column(nullable = false, columnDefinition = "BIGINT DEFAULT 0")
    private Long balance;

    @OneToMany(mappedBy = "wallet")
    private List<WalletTransaction> walletTransactions = new ArrayList<>();

    public static Wallet create(User user) {
        Wallet wallet = new Wallet();
        // @MapsId가 이 필드에서 id를 파생
        wallet.user = user;
        wallet.balance = 0L;
        return wallet;
    }
}
