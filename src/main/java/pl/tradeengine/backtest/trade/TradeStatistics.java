package pl.tradeengine.backtest.trade;

import java.time.Duration;
import java.util.List;

/**
 * Summary of closed trades. A trade closed at exactly zero (a break-even stop) is neither a win
 * nor a loss: it counts in {@code trades} and breaks both streaks. Drawdown is measured on the
 * balance after each closed trade, not on floating equity inside a trade, so it understates the
 * real intra-trade dip.
 */
public record TradeStatistics(
        double initialCapital,
        double finalCapital,
        int trades,
        int wins,
        int losses,
        int breakEvens,
        double netPnl,
        double returnPct,
        double grossProfit,
        double grossLoss,
        double profitFactor,
        double winRatePct,
        double expectancy,
        double avgWin,
        double avgLoss,
        double avgWinToAvgLoss,
        double avgR,
        double largestWin,
        double largestLoss,
        double maxDrawdown,
        double maxDrawdownPct,
        int maxWinStreak,
        int maxLossStreak,
        Duration avgHolding
) {

    public static TradeStatistics of(List<Trade> allTrades, double initialCapital) {
        List<Trade> trades = allTrades.stream().filter(Trade::closed).toList();
        int n = trades.size();

        double grossProfit = 0, grossLoss = 0, sumR = 0, largestWin = 0, largestLoss = 0;
        int wins = 0, losses = 0, breakEvens = 0, streakW = 0, streakL = 0, maxW = 0, maxL = 0;
        double equity = initialCapital, peak = initialCapital, maxDd = 0, maxDdPct = 0;
        Duration holding = Duration.ZERO;

        for (Trade t : trades) {
            if (t.pnl() > 0) {
                wins++;
                grossProfit += t.pnl();
                largestWin = Math.max(largestWin, t.pnl());
                streakW++;
                streakL = 0;
            } else if (t.pnl() == 0) {
                breakEvens++;
                streakW = 0;
                streakL = 0;
            } else {
                losses++;
                grossLoss += t.pnl();
                largestLoss = Math.min(largestLoss, t.pnl());
                streakL++;
                streakW = 0;
            }
            maxW = Math.max(maxW, streakW);
            maxL = Math.max(maxL, streakL);
            sumR += t.rMultiple();
            holding = holding.plus(Duration.between(t.entryTime(), t.exitTime()));

            equity += t.pnl();
            peak = Math.max(peak, equity);
            maxDd = Math.max(maxDd, peak - equity);
            maxDdPct = Math.max(maxDdPct, (peak - equity) / peak * 100);
        }

        double net = grossProfit + grossLoss;
        double avgWin = wins == 0 ? 0 : grossProfit / wins;
        double avgLoss = losses == 0 ? 0 : grossLoss / losses;
        return new TradeStatistics(
                initialCapital,
                initialCapital + net,
                n,
                wins,
                losses,
                breakEvens,
                net,
                net / initialCapital * 100,
                grossProfit,
                grossLoss,
                grossLoss == 0 ? Double.POSITIVE_INFINITY : grossProfit / -grossLoss,
                n == 0 ? 0 : 100.0 * wins / n,
                n == 0 ? 0 : net / n,
                avgWin,
                avgLoss,
                avgLoss == 0 ? Double.POSITIVE_INFINITY : avgWin / -avgLoss,
                n == 0 ? 0 : sumR / n,
                largestWin,
                largestLoss,
                maxDd,
                maxDdPct,
                maxW,
                maxL,
                n == 0 ? Duration.ZERO : holding.dividedBy(n));
    }
}
