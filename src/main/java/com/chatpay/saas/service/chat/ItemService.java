package com.chatpay.saas.service.chat;

import com.chatpay.saas.domain.Item;
import com.chatpay.saas.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;

    public Item getOrCreateItem(String externalItemId, String itemName, Long itemPrice) {
        Optional<Item> existingItem = itemRepository.findByExternalItemId(externalItemId);
        Item item;
        if (existingItem.isPresent()) {
            item = existingItem.get();
            item.update(itemName, itemPrice);
        } else {
            item = Item.create(externalItemId, itemName, itemPrice);
        }
        itemRepository.save(item);
        return item;
    }
}
