package com.example.recordshop.web.catalog;

import com.example.recordshop.domain.catalog.Format;
import com.example.recordshop.domain.catalog.Pressing;
import com.example.recordshop.domain.catalog.Release;
import com.example.recordshop.domain.catalog.ReleaseId;
import com.example.recordshop.domain.catalog.ReleaseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Catalog コンテキストの Release に対する最小限の REST API。
 *
 * <p>「歩く骨格」の第一歩として、まずは登録と取得のみを実装している。
 * Pressing の追加・出品(Listing)との連携は後続のフェーズで追加する。
 */
@RestController
@RequestMapping("/api/releases")
public class ReleaseController {

    private final ReleaseRepository releaseRepository;

    public ReleaseController(ReleaseRepository releaseRepository) {
        this.releaseRepository = releaseRepository;
    }

    @PostMapping
    public ResponseEntity<ReleaseResponse> register(@RequestBody RegisterReleaseRequest request) {
        Release release = Release.register(
                ReleaseId.generate(),
                request.title(),
                request.artistName(),
                request.genres(),
                request.originalReleaseYear()
        );
        releaseRepository.save(release);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReleaseResponse.from(release));
    }

    @GetMapping("/{releaseId}")
    public ReleaseResponse findById(@PathVariable String releaseId) {
        Release release = releaseRepository.findById(ReleaseId.of(releaseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));
        return ReleaseResponse.from(release);
    }

    @PostMapping("/{releaseId}/pressings")
    public ResponseEntity<PressingResponse> addPressing(@PathVariable String releaseId,
                                                          @RequestBody AddPressingRequest request) {
        Release release = releaseRepository.findById(ReleaseId.of(releaseId))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Release not found: " + releaseId));
        Pressing pressing = release.addPressing(
                request.labelName(),
                request.catalogNumber(),
                request.country(),
                request.pressYear(),
                request.matrixRunout(),
                request.reissue(),
                Format.vinyl(request.mediaType(), request.speed(), request.discCount())
        );
        releaseRepository.save(release);
        return ResponseEntity.status(HttpStatus.CREATED).body(PressingResponse.from(pressing));
    }
}
