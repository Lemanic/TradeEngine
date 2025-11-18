package pl.tradeengine.adapter.inbound;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.application.sevice.WebhookProcessingService;

@RestController
@RequestMapping("/webhooks")
public class TradingViewWebhookController {

    private final WebhookProcessingService webhookProcessingService;

    public TradingViewWebhookController(WebhookProcessingService webhookProcessingService) {
        this.webhookProcessingService = webhookProcessingService;
    }

    @PostMapping("/fvg")
    public ResponseEntity<Void> fvg(@RequestBody FvgAlertDto dto) {
        webhookProcessingService.handleFvg(dto);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/divergence")
    public ResponseEntity<Void> divergence(@RequestBody DivergenceAlertDto dto) {
        webhookProcessingService.handleDivergence(dto);
        return ResponseEntity.accepted().build();
    }

    @PostMapping("/price")
    public ResponseEntity<Void> price(@RequestBody PriceUpdateDto dto) {
        webhookProcessingService.handlePriceUpdate(dto);
        return ResponseEntity.accepted().build();
    }
}
