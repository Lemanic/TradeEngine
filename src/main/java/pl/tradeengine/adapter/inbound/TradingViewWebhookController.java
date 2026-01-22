package pl.tradeengine.adapter.inbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.MomentumAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.application.service.WebhookProcessingService;


@Slf4j
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

    @PostMapping("/momentum")
    public ResponseEntity<Void> momentum(@RequestBody MomentumAlertDto dto) {
        log.info("Received Momentum Alert: {}", dto);
        try {
            webhookProcessingService.handleMomentumAlert(dto);
            return ResponseEntity.accepted().build();
        } catch (Exception e) {
            log.error("Error processing momentum alert: {}", e.getMessage(), e);
            return ResponseEntity.badRequest().build();
        }
    }


}
