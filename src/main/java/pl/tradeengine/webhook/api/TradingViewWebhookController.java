package pl.tradeengine.webhook.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.tradeengine.alerts.app.AlertIngestService;
import pl.tradeengine.webhook.dto.CandlePriceDto;
import pl.tradeengine.webhook.dto.DivergenceAlertDto;
import pl.tradeengine.webhook.dto.FvgAlertDto;

@RestController
@RequestMapping("/api/webhooks")
public class TradingViewWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TradingViewWebhookController.class);
    private final AlertIngestService alertIngestService;

    public TradingViewWebhookController(AlertIngestService alertIngestService) {
        this.alertIngestService = alertIngestService;
    }

    @PostMapping("/divergence")
    public ResponseEntity<Void> receiveDivergenceAlert(@Valid @RequestBody DivergenceAlertDto dto) {
        log.info("Received divergence alert: {}", dto);
        alertIngestService.handleDivergence(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fvg")
    public ResponseEntity<Void> receiveFvgAlert(@Valid @RequestBody FvgAlertDto dto) {
        log.info("Received FVG alert: {}", dto);
        alertIngestService.handleFvgCreate(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/candle_update")
    public ResponseEntity<Void> updateCandlePrices(@Valid @RequestBody CandlePriceDto dto) {
        log.info("Received candle update: {}", dto);
        alertIngestService.updateFvgStatus(dto);
        return ResponseEntity.ok().build();
    }

}
