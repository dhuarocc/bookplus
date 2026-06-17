package com.bookplus.catalog.adapter.in.web;

import com.bookplus.catalog.adapter.in.web.dto.BookResponse;
import com.bookplus.catalog.adapter.in.web.preview.PdfPreviewService;
import com.bookplus.catalog.adapter.out.persistence.entity.UserPurchaseEntity;
import com.bookplus.catalog.adapter.out.persistence.repository.UserPurchaseJpaRepository;
import com.bookplus.catalog.domain.model.BookId;
import com.bookplus.catalog.domain.port.out.LoadBookPort;
import com.bookplus.catalog.shared.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Biblioteca del usuario: libros comprados (acceso al PDF completo).
 * Ruta en el gateway: /api/v1/library/** → catalog-service (requiere auth).
 */
@RestController
@RequestMapping("/api/v1/library")
@RequiredArgsConstructor
@Tag(name = "Library", description = "Purchased books & downloads")
@SecurityRequirement(name = "bearerAuth")
public class LibraryController {

    private final UserPurchaseJpaRepository purchaseRepo;
    private final PdfPreviewService         previewService;
    private final LoadBookPort              loadBookPort;

    @GetMapping
    @Operation(summary = "List books I purchased")
    public ResponseEntity<ApiResponse<List<BookResponse>>> myLibrary(@AuthenticationPrincipal Jwt jwt) {
        List<BookResponse> books = purchaseRepo.findByUserIdAndActiveTrueOrderByPurchasedAtDesc(jwt.getSubject())
                .stream()
                .map(p -> loadBookPort.findById(BookId.of(p.getBookId().toString())).orElse(null))
                .filter(Objects::nonNull)
                .map(BookResponse::from)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(books));
    }

    @GetMapping("/{bookId}/book.pdf")
    @Operation(summary = "Download/read the full PDF of a purchased book (or admin)")
    public ResponseEntity<byte[]> download(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId) {

        boolean admin = hasAdminRole(jwt);
        boolean owner = purchaseRepo.existsByUserIdAndBookIdAndActiveTrue(jwt.getSubject(), bookId);
        if (!admin && !owner) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este libro");
        }

        // Marcar como descargado (relevante para la política de reembolsos de digitales).
        if (owner) {
            purchaseRepo.findByUserIdAndBookId(jwt.getSubject(), bookId).ifPresent(p -> {
                if (!p.isDownloaded()) {
                    p.setDownloaded(true);
                    purchaseRepo.save(p);
                }
            });
        }

        return previewService.getPreview(bookId)
                .filter(p -> p.getFullPdf() != null)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_PDF)
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"book.pdf\"")
                        .header("X-Total-Pages",
                                p.getFullPages() == null ? "" : String.valueOf(p.getFullPages()))
                        .cacheControl(CacheControl.noCache())
                        .body(p.getFullPdf()))
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PutMapping("/{bookId}/progress")
    @Operation(summary = "Report reading progress (0-100) for a purchased book")
    public ResponseEntity<ApiResponse<Integer>> updateProgress(
            @AuthenticationPrincipal Jwt jwt, @PathVariable UUID bookId,
            @RequestParam("percent") int percent) {

        int clamped = Math.max(0, Math.min(100, percent));
        UserPurchaseEntity purchase = purchaseRepo
                .findByUserIdAndBookId(jwt.getSubject(), bookId)
                .filter(UserPurchaseEntity::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso a este libro"));

        // El progreso solo avanza (evita que un reset baje el umbral de "consumido").
        if (clamped > purchase.getReadProgress()) {
            purchase.setReadProgress(clamped);
            purchase.setDownloaded(true);
            purchaseRepo.save(purchase);
        }
        return ResponseEntity.ok(ApiResponse.ok(purchase.getReadProgress()));
    }

    @SuppressWarnings("unchecked")
    private boolean hasAdminRole(Jwt jwt) {
        Object roles = jwt.getClaim("roles");
        if (roles instanceof List<?> list) {
            for (Object r : list) {
                String s = String.valueOf(r);
                if ("ROLE_EDITOR".equals(s) || "ROLE_ADMIN".equals(s) || "ROLE_SUPERADMIN".equals(s)) {
                    return true;
                }
            }
        }
        return false;
    }
}
