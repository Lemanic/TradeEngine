package pl.tradeengine.adapter.inbound;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.application.dto.portfolio.ApiErrorResponse;
import pl.tradeengine.application.dto.portfolio.PortfolioHistoryResponse;
import pl.tradeengine.application.dto.portfolio.ManualHoldingResponse;
import pl.tradeengine.application.dto.portfolio.PortfolioSummaryResponse;
import pl.tradeengine.application.dto.portfolio.UpdateManualHoldingRequest;
import pl.tradeengine.application.dto.portfolio.SnapshotCreatedResponse;
import pl.tradeengine.application.service.portfolio.ManualHoldingService;
import pl.tradeengine.application.service.portfolio.PortfolioQueryService;
import pl.tradeengine.application.service.portfolio.PortfolioSnapshotService;
import pl.tradeengine.domain.model.portfolio.FiatCurrency;
import pl.tradeengine.domain.model.portfolio.ManualHolding;
import pl.tradeengine.domain.model.portfolio.HistoryRange;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;

/**
 * Portfolio API. The shapes of {@code /summary} and {@code /history} are fixed by
 * docs/portfolio-api.md — that document is the source of truth, not this class.
 *
 * <p>No CORS mapping and no authentication here on purpose: the dashboard is served from the
 * same origin, and in production Cloudflare Access guards {@code /portfolio*} and
 * {@code /api/portfolio*} (TradingView webhooks must stay open, so the policy never covers the
 * whole domain).
 */
@RestController
@RequestMapping("/api/portfolio")
@Tag(name = "Portfolio", description = "Wartość portfela krypto: stan bieżący i historia.")
public class PortfolioController {

    private final PortfolioQueryService queryService;
    private final PortfolioSnapshotService snapshotService;
    private final ManualHoldingService manualHoldings;

    public PortfolioController(PortfolioQueryService queryService,
                               PortfolioSnapshotService snapshotService,
                               ManualHoldingService manualHoldings) {
        this.queryService = queryService;
        this.snapshotService = snapshotService;
        this.manualHoldings = manualHoldings;
    }

    @Operation(summary = "Stan portfela z ostatniego snapshotu")
    @ApiResponse(responseCode = "200", description = "Ostatni snapshot")
    @ApiResponse(responseCode = "204", description = "Nie ma jeszcze żadnego snapshotu",
            content = @Content)
    @ApiResponse(responseCode = "400", description = "Nieznana waluta",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/summary")
    public ResponseEntity<PortfolioSummaryResponse> summary(
            @Parameter(description = "Waluta kwot; domyślnie USD")
            @RequestParam(required = false) String currency) {

        return queryService.summary(parseCurrency(currency))
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "Szereg czasowy wartości portfela")
    @ApiResponse(responseCode = "200", description = "Punkty w zadanym zakresie; może być pusto")
    @ApiResponse(responseCode = "400", description = "Brak albo nieznany range, nieznana waluta",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @GetMapping("/history")
    public PortfolioHistoryResponse history(
            @Parameter(description = "Zakres historii", required = true,
                    schema = @Schema(implementation = HistoryRange.class))
            @RequestParam(required = false) String range,
            @Parameter(description = "Waluta kwot; domyślnie USD")
            @RequestParam(required = false) String currency) {

        return queryService.history(parseRange(range), parseCurrency(currency));
    }

    @Operation(summary = "Ręczny snapshot — zastępuje ten z bieżącej godziny",
            description = """
                    Endpoint operatorski, poza kontraktem dashboardu: zwraca snapshot tak,
                    jak został zapisany, w obu walutach i z pełną precyzją ilości.""")
    @ApiResponse(responseCode = "200", description = "Snapshot zapisany")
    @ApiResponse(responseCode = "503", description = "Nic nie zapisano: brak źródła albo brak cen",
            content = @Content)
    @PostMapping("/snapshots")
    public ResponseEntity<SnapshotCreatedResponse> takeSnapshot() {
        return snapshotService.takeSnapshot()
                .map(snapshot -> ResponseEntity.ok(SnapshotCreatedResponse.from(snapshot)))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build());
    }

    @Operation(summary = "Pozycje, których ilość podaje człowiek",
            description = "Rejestr tego, co wpisano — bez kwot i bez cen. Lista pozycji pochodzi "
                    + "z konfiguracji; przez API zmienia się wyłącznie ilość.")
    @GetMapping("/manual-holdings")
    public List<ManualHoldingResponse> manualHoldings() {
        return manualHoldings.list().stream().map(ManualHoldingResponse::from).toList();
    }

    @Operation(summary = "Zmiana ilości ręcznej pozycji",
            description = "Zmiana jest widoczna w najbliższym snapshocie, nie natychmiast — "
                    + "summary i history zmienią się po kolejnym zbieraniu. Zero jest poprawne "
                    + "i znaczy „nic tu nie trzymam”: pozycja zostaje w konfiguracji i wróci, "
                    + "gdy ilość urośnie.")
    @ApiResponse(responseCode = "200", description = "Ilość zapisana")
    @ApiResponse(responseCode = "400", description = "Ilość ujemna, nieliczbowa albo brakująca",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Nieznany key",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping("/manual-holdings/{key}")
    public ManualHoldingResponse updateManualHolding(
            @PathVariable String key,
            @RequestBody(required = false) UpdateManualHoldingRequest request) {

        BigDecimal amount = request == null ? null : request.amount();
        if (amount == null) {
            throw new BadRequestException("missing_amount", "Pole amount jest wymagane");
        }
        if (amount.signum() < 0) {
            throw new BadRequestException("invalid_amount",
                    "Ilość nie może być ujemna, a jest " + amount.toPlainString());
        }

        return manualHoldings.updateAmount(key, amount)
                .map(ManualHoldingResponse::from)
                .orElseThrow(() -> new NotFoundException("unknown_key",
                        "Nie ma ręcznej pozycji o key '" + key + "'. Dostępne: "
                                + manualHoldings.list().stream().map(ManualHolding::key).toList()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> onNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(e.code(), e.getMessage()));
    }

    /** Ciało, którego Jackson nie potrafi sparsować — np. amount jako tekst albo NaN. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiErrorResponse> onUnreadableBody(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse("invalid_body",
                "Ciało żądania nie jest poprawnym JSON-em z liczbowym polem amount"));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> onBadRequest(BadRequestException e) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(e.code(), e.getMessage()));
    }

    /** Absent means USD. Anything other than the exact codes is a 400, as the contract says. */
    private static FiatCurrency parseCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return FiatCurrency.USD;
        }
        return Arrays.stream(FiatCurrency.values())
                .filter(value -> value.name().equals(currency))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("invalid_currency",
                        "Nieznana waluta '" + currency + "'. Dozwolone: USD, PLN"));
    }

    private static HistoryRange parseRange(String range) {
        if (range == null || range.isBlank()) {
            throw new BadRequestException("missing_range",
                    "Parametr range jest wymagany. Dozwolone: " + HistoryRange.codes());
        }
        return HistoryRange.fromCode(range)
                .orElseThrow(() -> new BadRequestException("invalid_range",
                        "Nieznany range '" + range + "'. Dozwolone: " + HistoryRange.codes()));
    }
}
