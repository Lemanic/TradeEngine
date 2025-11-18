package pl.tradeengine.alerts.infra.jpa;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import pl.tradeengine.alerts.domain.Direction;

import java.time.Instant;

//@Getter
//@Setter
//@Entity
//@Table(name = "divergences")
public class DivergenceEntity {
//
//    @Id
//    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    private String timeframe;

//    @Enumerated(EnumType.STRING)
    private Direction direction;

    @NotNull
    private double strength;

    private Instant timestamp;

    // Konstruktor, gettery, settery

    protected DivergenceEntity() {}

    public DivergenceEntity(String symbol, String timeframe, Direction direction, double strength, Instant timestamp) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.strength = strength;
        this.timestamp = timestamp;
    }
}
