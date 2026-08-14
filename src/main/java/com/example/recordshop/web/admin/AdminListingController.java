package com.example.recordshop.web.admin;

import com.example.recordshop.domain.catalog.PressingId;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.ConditionType;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingId;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.shared.Money;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.Instant;
import java.util.Currency;

/**
 * 出品者向け管理画面: Listing の作成・公開(POST /admin/listings/**)。
 * {@code SecurityConfig} により ROLE_ADMIN のみアクセス可能。
 */
@Controller
public class AdminListingController {

    private final ListingRepository listingRepository;
    private final ReleaseRepository releaseRepository;

    public AdminListingController(ListingRepository listingRepository, ReleaseRepository releaseRepository) {
        this.listingRepository = listingRepository;
        this.releaseRepository = releaseRepository;
    }

    @PostMapping("/admin/listings")
    public String create(@ModelAttribute ListingForm form, RedirectAttributes redirectAttributes) {
        PressingId pressingId = PressingId.of(form.getPressingId());
        Release release = releaseRepository.findByPressingId(pressingId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Pressing not found: " + form.getPressingId()));

        try {
            Currency currency = Currency.getInstance(form.getPriceCurrency());
            Money price = new Money(form.getPriceAmount(), currency);

            Listing listing;
            if (form.getConditionType() == ConditionType.USED) {
                listing = Listing.usedCopy(ListingId.generate(), pressingId, price,
                        form.getVinylGrade(), form.getSleeveGrade(), form.getSellerNote());
            } else {
                int initialStock = form.getInitialStock() != null ? form.getInitialStock() : 1;
                listing = Listing.newCopy(ListingId.generate(), pressingId, price, initialStock);
            }
            listingRepository.save(listing);
        } catch (IllegalArgumentException | NullPointerException e) {
            redirectAttributes.addFlashAttribute("errorMessage", "出品の登録に失敗しました: " + e.getMessage());
        }

        return "redirect:/admin/releases/" + release.releaseId();
    }

    @PostMapping("/admin/listings/{listingId}/publish")
    public String publish(@PathVariable String listingId, RedirectAttributes redirectAttributes) {
        Listing listing = listingRepository.findById(ListingId.of(listingId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Listing not found: " + listingId));

        try {
            listing.publish(Instant.now());
            listingRepository.save(listing);
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        Release release = releaseRepository.findByPressingId(listing.pressingId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found"));
        return "redirect:/admin/releases/" + release.releaseId();
    }
}
