package pl.tradeengine.adapter.inbound;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import pl.tradeengine.domain.model.DivergenceSignal;
import pl.tradeengine.domain.port.DivergenceRepository;
import pl.tradeengine.domain.port.FvgRepository;

import java.util.List;

@RestController
@RequestMapping("/api/signals")
public class SignalController {

    private final DivergenceRepository divergenceRepository;
    private final FvgRepository fvgRepository;


    public SignalController(DivergenceRepository divergenceRepository, FvgRepository fvgRepository) {
        this.divergenceRepository = divergenceRepository;
        this.fvgRepository = fvgRepository;
    }

    @GetMapping("/divergences")
    public ResponseEntity<List<DivergenceSignal>> getDivergences() {
        return ResponseEntity.ok(divergenceRepository.findAll());
    }

}