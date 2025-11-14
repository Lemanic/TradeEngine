package pl.tradeengine.webhook.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.tradeengine.webhook.dto.TradingViewAlertDto;

@RestController
@RequestMapping("/api/webhooks")
public class TradingViewWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TradingViewWebhookController.class);

    @PostMapping("/tradingview")
    public ResponseEntity<Void> receiveTradingViewAlert(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @Valid @RequestBody TradingViewAlertDto payload
    ) {
        log.info(
                "Received TV alert: secretPresent={}, symbol={}, interval={}, type={}, dir={}, strength={}",
                secret != null && !secret.isBlank(),
                payload.symbol(),
                payload.interval(),
                payload.signalType(),
                payload.direction(),
                payload.strength()
        );
        log.debug("Full payload: {}", payload);
        return ResponseEntity.ok().build();
    }
}
