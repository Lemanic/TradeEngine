package pl.tradeengine.domain.model;

public enum FvgKind {
    FVG,
    IFVG;

    public static FvgKind fromSignalType(String signalType) {
        if (signalType == null || signalType.isBlank()) {
            throw new IllegalArgumentException("signalType must not be null or blank");
        }

        if (signalType.startsWith("IFVG_")) {
            return IFVG;
        }

        if (signalType.startsWith("FVG_")) {
            return FVG;
        }

        throw new IllegalArgumentException("Unsupported signalType: " + signalType);
    }
}
