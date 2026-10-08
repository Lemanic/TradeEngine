package pl.tradeengine.application.dto.portfolio;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "ChangeSet", description = """
        Zmiany w trzech horyzontach. Każde pole jest null, gdy nie ma snapshotu starszego
        o zadany okres (albo gdy tamten total wynosił zero i procent nie istnieje).""")
public record ChangeSetDto(

        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        ChangeDto h24,

        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        ChangeDto d7,

        @Schema(nullable = true, requiredMode = Schema.RequiredMode.REQUIRED)
        ChangeDto d30
) {}
