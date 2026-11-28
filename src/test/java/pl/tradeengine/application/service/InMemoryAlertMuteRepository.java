package pl.tradeengine.application.service;

import pl.tradeengine.domain.model.AlertMute;
import pl.tradeengine.domain.model.Symbol;
import pl.tradeengine.domain.model.Timeframe;
import pl.tradeengine.domain.port.AlertMuteRepository;

import java.util.ArrayList;
import java.util.List;

/** Fake zgodny z konwencja repo: repozytoriow nie mockujemy. */
public class InMemoryAlertMuteRepository implements AlertMuteRepository {

    private final List<AlertMute> mutes = new ArrayList<>();

    @Override
    public List<AlertMute> findAll() {
        return List.copyOf(mutes);
    }

    @Override
    public AlertMute save(AlertMute mute) {
        delete(mute.strategy(), mute.symbol(), mute.timeframe());
        mutes.add(mute);
        return mute;
    }

    @Override
    public void delete(String strategy, Symbol symbol, Timeframe timeframe) {
        mutes.removeIf(mute -> mute.sameRuleAs(strategy, symbol, timeframe));
    }

    public void clear() {
        mutes.clear();
    }
}
