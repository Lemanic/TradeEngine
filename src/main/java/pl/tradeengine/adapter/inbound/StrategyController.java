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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.application.dto.portfolio.ApiErrorResponse;
import pl.tradeengine.application.dto.strategy.AlertMuteDto;
import pl.tradeengine.application.dto.strategy.StrategiesResponse;
import pl.tradeengine.application.service.AlertMuteService;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;

import java.util.Arrays;

/**
 * Strategies and their muted pairs, for the dashboard. Served under the portfolio host, so it sits
 * behind the same Cloudflare Access; on the webhook host the WAF rule blocks everything outside
 * {@code /webhooks/*}.
 *
 * <p>Symbol and timeframe travel as query parameters rather than path segments: TradingView
 * tickers may carry characters such as {@code !} or {@code :}, which a query string encodes
 * without surprises.
 */
@RestController
@RequestMapping("/api/strategies")
@Tag(name = "Strategie", description = "Strategie z app.strategies i wyciszone na nich pary.")
public class StrategyController {

    /** Set by Cloudflare Access on every request it lets through; absent on localhost. */
    static final String ACCESS_EMAIL_HEADER = "Cf-Access-Authenticated-User-Email";

    private final AlertMuteService muteService;

    public StrategyController(AlertMuteService muteService) {
        this.muteService = muteService;
    }

    @Operation(summary = "Strategie, znane pary i wyciszenia")
    @GetMapping
    public StrategiesResponse strategies() {
        return StrategiesResponse.from(muteService.overview());
    }

    @Operation(summary = "Wycisz alerty strategii na parze",
            description = "Działa od następnego alertu, bez restartu. Strategia dalej dostaje "
                    + "eventy — tłumiona jest tylko wysyłka, więc po zdjęciu wyciszenia działa od "
                    + "razu poprawnie. Powtórne wyciszenie zwraca istniejącą regułę.")
    @ApiResponse(responseCode = "200", description = "Para wyciszona")
    @ApiResponse(responseCode = "400", description = "Brak symbolu albo nieznany interwał",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Strategii nie ma w app.strategies",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @PutMapping("/{name}/mutes")
    public AlertMuteDto mute(
            @PathVariable String name,
            @Parameter(description = "Symbol dokładnie tak, jak przychodzi w webhooku", required = true)
            @RequestParam(required = false) String symbol,
            @Parameter(description = "Pominięty = wszystkie interwały",
                    schema = @Schema(implementation = Timeframe.class))
            @RequestParam(required = false) String timeframe,
            @Parameter(hidden = true)
            @RequestHeader(name = ACCESS_EMAIL_HEADER, required = false) String accessEmail) {

        String mutedBy = accessEmail == null || accessEmail.isBlank() ? null : accessEmail.trim();

        return muteService.mute(name, parseSymbol(symbol), parseTimeframe(timeframe), mutedBy)
                .map(AlertMuteDto::from)
                .orElseThrow(() -> unknownStrategy(name));
    }

    @Operation(summary = "Zdejmij wyciszenie",
            description = "Usuwa dokładnie tę regułę: zdjęcie reguły H1 nie rusza reguły na "
                    + "wszystkie interwały dla tej samej pary i odwrotnie. Brak reguły to też 204.")
    @ApiResponse(responseCode = "204", description = "Reguły już nie ma")
    @ApiResponse(responseCode = "400", description = "Brak symbolu albo nieznany interwał",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @ApiResponse(responseCode = "404", description = "Strategii nie ma w app.strategies",
            content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                    schema = @Schema(implementation = ApiErrorResponse.class)))
    @DeleteMapping("/{name}/mutes")
    public ResponseEntity<Void> unmute(
            @PathVariable String name,
            @RequestParam(required = false) String symbol,
            @Parameter(schema = @Schema(implementation = Timeframe.class))
            @RequestParam(required = false) String timeframe) {

        if (!muteService.unmute(name, parseSymbol(symbol), parseTimeframe(timeframe))) {
            throw unknownStrategy(name);
        }
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiErrorResponse> onNotFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiErrorResponse(e.code(), e.getMessage()));
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiErrorResponse> onBadRequest(BadRequestException e) {
        return ResponseEntity.badRequest().body(new ApiErrorResponse(e.code(), e.getMessage()));
    }

    private NotFoundException unknownStrategy(String name) {
        return new NotFoundException("unknown_strategy",
                "Nie ma strategii '" + name + "' w app.strategies. Dostępne: "
                        + muteService.strategyNames());
    }

    /** Trimmed only — the rule must match the webhook's symbol exactly, so no case folding. */
    private static Symbol parseSymbol(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new BadRequestException("missing_symbol", "Parametr symbol jest wymagany");
        }
        return new Symbol(symbol.trim());
    }

    /** Absent means every timeframe. */
    private static Timeframe parseTimeframe(String timeframe) {
        if (timeframe == null || timeframe.isBlank()) {
            return null;
        }
        return Arrays.stream(Timeframe.values())
                .filter(value -> value.name().equals(timeframe))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("invalid_timeframe",
                        "Nieznany interwał '" + timeframe + "'. Dozwolone: "
                                + Arrays.toString(Timeframe.values())));
    }
}
