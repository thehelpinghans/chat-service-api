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
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "external_id"})
})
public class User extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 토스의 customerKey에 해당 — 테넌트 시스템의 유저 식별자
    @Column(nullable = false, length = 255)
    private String externalId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private UserStatus status;

    @OneToMany(mappedBy = "user")
    private List<ChatRoom> chatRooms = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<ChatMessage> chatMessages = new ArrayList<>();

    @OneToMany(mappedBy = "user")
    private List<Trade> trades = new ArrayList<>();

    // Wallet 참조 필드는 의도적으로 두지 않음.
    // 원인: mappedBy(inverse) @OneToOne은 FK가 없는 쪽이라 FetchType.LAZY를 붙여도 바이트코드 인핸스먼트 없이는 프록시를 못 만들고,
    //      User 로드 시마다 "Wallet 존재 여부" 확인 쿼리가 getWallet() 호출 여부와 무관하게 무조건 자동 실행됨. (참고: https://vladmihalcea.com/hibernate-lazytoone-annotation/)
    // 조치: 이 필드를 제거해 그 자동 쿼리 자체를 없앰.
    // 대안: User -> Wallet 탐색이 필요해지면, 급할 땐 walletRepository.findById(user.getId())로 필요한 곳에서만 직접 조회.
    //      자주 쓰게 되면 @LazyToOne(LazyToOneOption.NO_PROXY) Hibernate 바이트코드 인핸스먼트(build.gradle에 enhance 플러그인 추가) 적용 후 재도입.
    public static User create(String externalId) {
        User user = new User();
        user.externalId = externalId;
        user.status = UserStatus.ACTIVE;
        return user;
    }
}
