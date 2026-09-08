package com.chatpay.common.service;

import com.chatpay.Fixture;
import com.chatpay.common.domain.Item;
import com.chatpay.common.repository.ItemRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ItemServiceTest {

    @Mock
    private ItemRepository itemRepository;

    private ItemService itemService;

    private final Item existingItem = Fixture.createItem(1L, "item-1", "구 상품명", 5000L);

    private ItemService newItemService() {
        return new ItemService(itemRepository);
    }

    @Test
    @DisplayName("既存アイテムは価格・名前を更新")
    void updatesExistingItemWhenFound() {
        // given
        given(itemRepository.findByExternalItemId("item-1")).willReturn(Optional.of(existingItem));
        given(itemRepository.save(existingItem)).willReturn(existingItem);
        itemService = newItemService();

        // when
        Item result = itemService.getOrCreateItem("item-1", "새 상품명", 8000L);

        // then
        assertThat(result.getName()).isEqualTo("새 상품명");
        assertThat(result.getPrice()).isEqualTo(8000L);
        verify(itemRepository).save(existingItem);
    }

    @Test
    @DisplayName("アイテム新規生成")
    void createsNewItemWhenNotFound() {
        // given
        given(itemRepository.findByExternalItemId("item-2")).willReturn(Optional.empty());
        given(itemRepository.save(any(Item.class))).willAnswer(invocation -> invocation.getArgument(0));
        itemService = newItemService();

        // when
        Item result = itemService.getOrCreateItem("item-2", "새 상품", 3000L);

        // then
        assertThat(result.getExternalItemId()).isEqualTo("item-2");
        assertThat(result.getName()).isEqualTo("새 상품");
        assertThat(result.getPrice()).isEqualTo(3000L);
    }

    @Test
    @DisplayName("アイテム単件取得")
    void findsItemById() {
        // given
        given(itemRepository.findById(1L)).willReturn(Optional.of(existingItem));
        itemService = newItemService();

        // when
        Optional<Item> result = itemService.findItemById(1L);

        // then
        assertThat(result).contains(existingItem);
    }
}
