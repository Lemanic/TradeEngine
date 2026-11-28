package pl.tradeengine.application.dto.strategy;

import io.swagger.v3.oas.annotations.media.Schema;
import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Timeframe;

import java.time.Instant;

@Schema(name = "AlertMute", description = "Wyciszona para na jednej strategii.")
public record AlertMuteDto(

        @Schema(example = "GRINDER_SWING_D1_H1", requiredMode = Schema.RequiredMode.REQUIRED)
        String strategy,

        @Schema(example = "TAOUSDT", requiredMode = Schema.RequiredMode.REQUIRED)
        String symbol,

        @Schema(description = "null = wszystkie interwały tej strategii.", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        Timeframe timeframe,

        @Schema(example = "2026-11-28T09:15:00Z", requiredMode = Schema.RequiredMode.REQUIRED)
        Instant mutedAt,

        @Schema(description = "E-mail z Cloudflare Access; null, gdy żądanie ominęło Access "
                + "(localhost na maszynie prod). Tylko informacyjnie.",
                example = "someone@example.com", nullable = true,
                requiredMode = Schema.RequiredMode.REQUIRED)
        String mutedBy
) {

    public static AlertMuteDto from(AlertMute mute) {
        return new AlertMuteDto(mute.strategy(), mute.symbol().code(), mute.timeframe(),
                mute.mutedAt(), mute.mutedBy());
    }
}
