package pl.tradeengine.webhook.api;

import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pl.tradeengine.alerts.app.AlertIngestService;
import pl.tradeengine.application.dto.DivergenceAlertDto;
import pl.tradeengine.application.dto.FvgAlertDto;
import pl.tradeengine.application.dto.PriceUpdateDto;
import pl.tradeengine.application.sevice.WebhookProcessingService;
//import pl.tradeengine.alerts.domain.Direction;
//import pl.tradeengine.application.dto.PriceUpdateDto;
//import pl.tradeengine.application.sevice.WebhookProcessingService;
//import pl.tradeengine.domain.event.DivergenceDetectedEvent;
//import pl.tradeengine.domain.event.DomainEvent;
//import pl.tradeengine.domain.event.FvgCreatedEvent;
//import pl.tradeengine.domain.event.PriceCandleEvent;
//import pl.tradeengine.domain.model.*;
//import pl.tradeengine.webhook.dto.CandlePriceDto;
//import pl.tradeengine.webhook.dto.DivergenceAlertDto;
//import pl.tradeengine.webhook.dto.FvgAlertDto;

import java.time.ZonedDateTime;

//@RestController
//@RequestMapping("/webhooks")
public class TradingViewWebhookController {
    private static final Logger log = LoggerFactory.getLogger(TradingViewWebhookController.class);
//    private final AlertIngestService alertIngestService;

    private final WebhookProcessingService webhookProcessingService;

    public TradingViewWebhookController(WebhookProcessingService webhookProcessingService) {
        this.webhookProcessingService = webhookProcessingService;
    }

//    public TradingViewWebhookController(AlertIngestService alertIngestService) {
//        this.alertIngestService = alertIngestService;
//    }
//
//    @PostMapping("/divergence")
//    public ResponseEntity<Void> receiveDivergenceAlert(@Valid @RequestBody DivergenceAlertDto dto) {
//        log.info("Received divergence alert: {}", dto);
//        alertIngestService.handleDivergence(dto);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/fvg")
//    public ResponseEntity<Void> receiveFvgAlert(@Valid @RequestBody FvgAlertDto dto) {
//        log.info("Received FVG alert: {}", dto);
//        alertIngestService.handleFvgCreate(dto);
//        return ResponseEntity.ok().build();
//    }
//
//    @PostMapping("/candle_update")
//    public ResponseEntity<Void> updateCandlePrices(@Valid @RequestBody CandlePriceDto dto) {
//        log.info("Received candle update: {}", dto);
//        alertIngestService.updateFvgStatus(dto);
//        return ResponseEntity.ok().build();
//    }

//    @PostMapping("/fvg")
//        public ResponseEntity<Void> fvg(@RequestBody FvgAlertDto dto) {
//        webhookProcessingService.handleFvg(dto);
//        return ResponseEntity.accepted().build();
//    }
//
//    @PostMapping("/divergence")
//    public ResponseEntity<Void> divergence(@RequestBody DivergenceAlertDto dto) {
//        webhookProcessingService.handleDivergence(dto);
//        return ResponseEntity.accepted().build();
//    }
//
//    @PostMapping("/price")
//    public ResponseEntity<Void> price(@RequestBody PriceUpdateDto dto) {
//        webhookProcessingService.handlePriceUpdate(dto);
//        return ResponseEntity.accepted().build();
//    }

}
