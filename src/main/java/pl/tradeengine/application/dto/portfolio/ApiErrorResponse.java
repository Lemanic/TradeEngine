package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Ciało odpowiedzi błędnej. Kontrakt nie wymaga żadnego konkretnego kształtu, ale statyczny UI
 * czyta {@code error} i {@code message}, więc opłaca się je podać.
 */
@Schema(name = "ApiError", description = "Błąd żądania.")
public record ApiErrorResponse(

        @Schema(description = "Kod błędu do odczytu maszynowego.", example = "invalid_range",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String error,

        @Schema(description = "Opis dla człowieka.",
                example = "Nieznany range 'XX'. Dozwolone: 24H, 7D, 30D, 90D, 1Y, ALL",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String message
) {}
