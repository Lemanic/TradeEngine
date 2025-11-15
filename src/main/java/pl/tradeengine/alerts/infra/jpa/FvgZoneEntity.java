package pl.tradeengine.alerts.infra.jpa;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;

@Entity
@Table(name = "fvg_zone")
public class FvgZoneEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;

    private String timeframe;

    private String direction;

    private double strength;

    private double fvgLow;

    private double fvgHigh;

    private boolean active = true;

    // Konstruktor bezargumentowy (wymagany przez JPA)
    protected FvgZoneEntity() {}

    // Konstruktor wygodny do tworzenia obiektów
    public FvgZoneEntity(String symbol, String timeframe, String direction, double strength, double fvgLow, double fvgHigh) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.direction = direction;
        this.strength = strength;
        this.fvgLow = fvgLow;
        this.fvgHigh = fvgHigh;
        this.active = true;
    }

    // Gettery i settery jeżeli potrzebujesz (lub możesz użyć Lombok)

    public Long getId() {
        return id;
    }
    public String getSymbol() {
        return symbol;
    }
    public void setSymbol(String symbol) {
        this.symbol = symbol;
    }
    public String getTimeframe() {
        return timeframe;
    }
    public void setTimeframe(String timeframe) {
        this.timeframe = timeframe;
    }
    public String getDirection() {
        return direction;
    }
    public void setDirection(String direction) {
        this.direction = direction;
    }
    public double getStrength() {
        return strength;
    }
    public void setStrength(double strength) {
        this.strength = strength;
    }
    public double getFvgLow() {
        return fvgLow;
    }
    public void setFvgLow(double fvgLow) {
        this.fvgLow = fvgLow;
    }
    public double getFvgHigh() {
        return fvgHigh;
    }
    public void setFvgHigh(double fvgHigh) {
        this.fvgHigh = fvgHigh;
    }
    public boolean isActive() {
        return active;
    }
    public void setActive(boolean active) {
        this.active = active;
    }
}
