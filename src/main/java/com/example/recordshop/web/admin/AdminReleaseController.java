package com.example.recordshop.web.admin;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import com.example.recordshop.domain.inventory.Listing;
import com.example.recordshop.domain.inventory.ListingRepository;
import com.example.recordshop.domain.shared.InvariantViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 出品者向け管理画面: Release/Pressing の登録(GET/POST /admin/releases/**)。
 * {@code SecurityConfig} により ROLE_ADMIN のみアクセス可能。
 */
@Controller
@RequestMapping("/admin/releases")
public class AdminReleaseController {

    private final ReleaseRepository releaseRepository;
    private final ListingRepository listingRepository;

    public AdminReleaseController(ReleaseRepository releaseRepository, ListingRepository listingRepository) {
        this.releaseRepository = releaseRepository;
        this.listingRepository = listingRepository;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("releases", releaseRepository.findAll());
        return "admin/releases/list";
    }

    @GetMapping("/new")
    public String newForm(Model model) {
        if (!model.containsAttribute("releaseForm")) {
            model.addAttribute("releaseForm", new ReleaseForm());
        }
        return "admin/releases/new";
    }

    @PostMapping
    public String create(@ModelAttribute ReleaseForm form, Model model) {
        if (form.getTitle() == null || form.getTitle().isBlank()) {
            model.addAttribute("errorMessage", "タイトルを入力してください");
            model.addAttribute("releaseForm", form);
            return "admin/releases/new";
        }

        Set<String> genres = new LinkedHashSet<>();
        for (String genre : form.getGenres().split(",")) {
            if (!genre.isBlank()) {
                genres.add(genre.trim());
            }
        }

        try {
            Release release = Release.register(ReleaseId.generate(), form.getTitle(), form.getArtistName(),
                    genres, form.getOriginalReleaseYear());
            releaseRepository.save(release);
            return "redirect:/admin/releases/" + release.releaseId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("errorMessage", e.getMessage());
            model.addAttribute("releaseForm", form);
            return "admin/releases/new";
        }
    }

    @GetMapping("/{releaseId}")
    public String detail(@PathVariable String releaseId, Model model) {
        Release release = releaseRepository.findById(ReleaseId.of(releaseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));

        // 管理画面では公開状況を問わず全Listingを見せる(顧客向けcatalog画面はPUBLISHEDのみ)
        Map<Pressing, List<Listing>> pressingListings = new LinkedHashMap<>();
        for (Pressing pressing : release.pressings()) {
            pressingListings.put(pressing, listingRepository.findByPressingId(pressing.pressingId()));
        }

        model.addAttribute("release", release);
        model.addAttribute("pressingListings", pressingListings);
        if (!model.containsAttribute("pressingForm")) {
            model.addAttribute("pressingForm", new PressingForm());
        }
        if (!model.containsAttribute("listingForm")) {
            model.addAttribute("listingForm", new ListingForm());
        }
        return "admin/releases/detail";
    }

    @PostMapping("/{releaseId}/pressings")
    public String addPressing(@PathVariable String releaseId, @ModelAttribute PressingForm form,
                               RedirectAttributes redirectAttributes) {
        Release release = releaseRepository.findById(ReleaseId.of(releaseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));

        try {
            release.addPressing(form.getLabelName(), form.getCatalogNumber(), form.getCountry(), form.getPressYear(),
                    form.getMatrixRunout(), form.isReissue(),
                    Format.vinyl(form.getMediaType(), form.getSpeed(), form.getDiscCount()));
            releaseRepository.save(release);
        } catch (InvariantViolationException | IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
        }

        return "redirect:/admin/releases/" + releaseId;
    }
}
