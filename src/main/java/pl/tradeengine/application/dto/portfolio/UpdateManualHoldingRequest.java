package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;

@Schema(name = "UpdateManualHolding", description = "Nowa ilość. Zero jest poprawne i znaczy „nic tu nie trzymam”.")
public record UpdateManualHoldingRequest(

        @Schema(description = "Ilość w jednostkach aktywa; nieujemna.", example = "29.4",
                requiredMode = Schema.RequiredMode.REQUIRED)
        BigDecimal amount
) {}
