package com.clip2house.ledger.market;

import com.clip2house.ledger.catalog.ItemRepository;
import com.clip2house.ledger.persona.PersonaRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/feed")
public class FeedController {

    private final ListingRepository listings;
    private final ItemRepository items;
    private final PersonaRepository personas;

    public FeedController(ListingRepository listings, ItemRepository items, PersonaRepository personas) {
        this.listings = listings;
        this.items = items;
        this.personas = personas;
    }

    public record FeedEntry(Long listingId, long listingVersion, String itemName, String description,
                            String sellerNickname, Integer sellerAvgResponseSeconds,
                            Instant listedAt, Instant expiresAt) {}

    @GetMapping
    public List<FeedEntry> feed() {
        return listings.findTop100ByStatusOrderByCreatedAtDesc(Listing.Status.ACTIVE).stream()
                .map(listing -> {
                    var item = items.findById(listing.getItemId()).orElseThrow();
                    var persona = personas.findById(listing.getPersonaId()).orElseThrow();
                    return new FeedEntry(listing.getId(), listing.getVersion(), item.getName(),
                            item.getDescription(), persona.getNickname(), persona.getAvgResponseSeconds(),
                            listing.getCreatedAt(), listing.getExpiresAt());
                })
                .toList();
    }
}
