package pl.tradeengine.webhook.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.tradeengine.alerts.app.AlertIngestService;
import pl.tradeengine.webhook.dto.FvgAlertDto;
import pl.tradeengine.webhook.dto.TradingViewAlertDto;

@RestController
@RequestMapping("/api/webhooks")
public class TradingViewWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TradingViewWebhookController.class);
    private final AlertIngestService alertIngestService;

    public TradingViewWebhookController(AlertIngestService alertIngestService) {
        this.alertIngestService = alertIngestService;
    }

//    @PostMapping("/tradingview")
//    public ResponseEntity<Void> receiveTradingViewAlert(
//            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
//            @Valid @RequestBody TradingViewAlertDto payload
//    ) {
//        log.info("Received TV alert (raw DTO): {}", payload);
//        alertIngestService.handle(payload);
//        return ResponseEntity.ok().build();
//    }

    @PostMapping("/divergence")
    public ResponseEntity<Void> receiveDivergenceAlert(@Valid @RequestBody TradingViewAlertDto dto) {
        log.info("Received divergence alert: {}", dto);
        alertIngestService.handle(dto);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/fvg")
    public ResponseEntity<Void> receiveFvgAlert(@Valid @RequestBody FvgAlertDto dto) {
        log.info("Received FVG alert: {}", dto);
        alertIngestService.handleFvgCreate(dto);
        return ResponseEntity.ok().build();
    }
}
