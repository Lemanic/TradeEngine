package pl.tradeengine.adapter.inbound;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.domain.model.FvgStatus;
import pl.tradeengine.domain.port.FvgRepository;

@RestController
@RequestMapping("/admin/fvg")
public class FvgAdminController {

    private final FvgRepository fvgRepository;

    public FvgAdminController(FvgRepository fvgRepository) {
        this.fvgRepository = fvgRepository;
    }

    @PatchMapping("/{id}/consume")
    public ResponseEntity<Void> consume(@PathVariable Long id) {
        fvgRepository.updateStatus(id, FvgStatus.CONSUMED);
        return ResponseEntity.noContent().build();
    }
}
