package com.chatpay.common.service;

import com.chatpay.common.domain.Item;
import com.chatpay.common.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ItemService {

    private final ItemRepository itemRepository;

    @Transactional
    public Item getOrCreateItem(String externalItemId, String itemName, Long itemPrice) {
        Optional<Item> existingItem = itemRepository.findByExternalItemId(externalItemId);
        if (existingItem.isPresent()) {
            Item item = existingItem.get();
            item.update(itemName, itemPrice);
            itemRepository.save(item);
            return item;
        }

        return itemRepository.save(Item.create(externalItemId, itemName, itemPrice));
    }

    @Transactional(readOnly = true)
    public Optional<Item> findItemById(Long itemId) {
        return itemRepository.findById(itemId);
    }
}
