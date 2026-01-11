package pl.tradeengine.adapter.outbound.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import pl.tradeengine.domain.model.Timeframe;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "swing_point")
@Getter
@Setter
public class SwingPointEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    @Enumerated(EnumType.STRING)
    private Timeframe timeframe;

    private String type;

    @Column(precision = 32, scale = 16)
    private BigDecimal price;

    private ZonedDateTime detectedAt;

    public SwingPointEntity() {}

    public SwingPointEntity(String symbol, Timeframe timeframe, String type, BigDecimal price, ZonedDateTime detectedAt) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.type = type;
        this.price = price;
        this.detectedAt = detectedAt;
    }
}
