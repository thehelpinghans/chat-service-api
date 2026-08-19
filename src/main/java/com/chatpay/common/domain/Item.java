package com.chatpay.common.domain;

import com.chatpay.chat.domain.ChatRoom;
import com.chatpay.trade.domain.Trade;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(uniqueConstraints = {
        @UniqueConstraint(columnNames = {"tenant_id", "external_item_id"})
})
public class Item extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    //테넌트의 상품 pk
    @Column(nullable = false, length = 255)
    private String externalItemId;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false)
    private Long price;

    @OneToMany(mappedBy = "item")
    private List<ChatRoom> chatRooms = new ArrayList<>();

    @OneToMany(mappedBy = "item")
    private List<Trade> trades = new ArrayList<>();

    //빌더 메서드로 변경
    public static Item create(String externalItemId, String name, Long price) {
        validatePrice(price);
        Item item = new Item();
        item.externalItemId = externalItemId;
        item.name = name;
        item.price = price;
        return item;
    }

    public void update(String name, Long price) {
        validatePrice(price);
        this.name = name;
        this.price = price;
    }

    private static void validatePrice(Long price) {
        if (price == null || price <= 0) {
            throw new IllegalArgumentException("Item 가격 불변식 위반: price=" + price + " (price > 0 이어야 함)");
        }
    }
}
