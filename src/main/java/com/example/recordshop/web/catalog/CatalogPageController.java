package com.example.recordshop.web.catalog;

import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.inventory.ListingStatus;
import com.example.recordshop.web.PathIds;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 商品閲覧画面(GET /catalog, /catalog/{releaseId})。
 * 会員登録・ログインしていなくても閲覧できる(SecurityConfigでpermitAll)。
 */
@Controller
public class CatalogPageController {

    private final ReleaseRepository releaseRepository;
    private final ListingRepository listingRepository;

    public CatalogPageController(ReleaseRepository releaseRepository, ListingRepository listingRepository) {
        this.releaseRepository = releaseRepository;
        this.listingRepository = listingRepository;
    }

    @GetMapping("/catalog")
    public String list(Model model) {
        List<Release> releases = releaseRepository.findAll();
        model.addAttribute("releases", releases);
        return "catalog/list";
    }

    @GetMapping("/catalog/{releaseId}")
    public String detail(@PathVariable String releaseId, Model model) {
        Release release = releaseRepository.findById(PathIds.parse(releaseId, ReleaseId::of, "Release"))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));

        // Pressing毎に「購入可能な(PUBLISHEDの)Listing」だけを紐づけて画面に渡す
        Map<Pressing, List<Listing>> pressingListings = new LinkedHashMap<>();
        for (Pressing pressing : release.pressings()) {
            List<Listing> availableListings = listingRepository.findByPressingId(pressing.pressingId()).stream()
                    .filter(listing -> listing.status() == ListingStatus.PUBLISHED)
                    .toList();
            pressingListings.put(pressing, availableListings);
        }

        model.addAttribute("release", release);
        model.addAttribute("pressingListings", pressingListings);
        return "catalog/detail";
    }
}
