package pl.tradeengine.adapter.inbound;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.application.dto.BiasAlertDto;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.application.dto.SwingPointDto;
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

    @PostMapping("/bias")
    public ResponseEntity<Void> bias(@RequestBody BiasAlertDto dto) {
        try {
            webhookProcessingService.handleBiasUpdate(dto);
            return ResponseEntity.accepted().build();
        } catch (IllegalArgumentException e) {
            log.error("Invalid BIAS data: {}", e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @PostMapping("/swing")
    public ResponseEntity<Void> swing(@RequestBody SwingPointDto dto) {
        webhookProcessingService.handleSwingPoint(dto);
        return ResponseEntity.accepted().build();
    }

}
