package pl.tradeengine.backtest.export;

import lombok.extern.slf4j.Slf4j;
import pl.tradeengine.backtest.trade.ExitReason;
import pl.tradeengine.backtest.trade.SignalOutcome;
import pl.tradeengine.backtest.trade.SignalPlan;
import pl.tradeengine.backtest.trade.Trade;
import pl.tradeengine.backtest.trade.TradeStatistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes the results of a trade backtest: trades and signals as CSV (for a spreadsheet) and one
 * self-contained HTML report with metrics, the equity curve and both tables.
 */
@Slf4j
public class TradeReportWriter {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    public void writeTradesCsv(List<Trade> trades, Path path) throws IOException {
        StringBuilder sb = new StringBuilder(
                "signal_time,entry_time,entry,stop_loss,take_profit,target_tf,quantity,exit_time,exit,exit_reason,pnl,r,equity_after,entry_type,direction\n");
        for (Trade t : trades) {
            sb.append(String.format(Locale.ROOT, "%s,%s,%.2f,%.2f,%.2f,%s,%.6f,%s,%.2f,%s,%.2f,%.3f,%.2f,%s,%s%n",
                    t.signalTime(), t.entryTime(), t.entryPrice(), t.stopLoss(), t.takeProfit(), t.targetTimeframe(),
                    t.quantity(), t.exitTime(), t.exitPrice(), t.exitReason(), t.pnl(), t.rMultiple(), t.equityAfter(), t.entryType(), t.direction()));
        }
        Files.writeString(path, sb);
        log.info("Trades CSV: {}", path);
    }

    public void writeSignalsCsv(List<SignalPlan> signals, Path path) throws IOException {
        StringBuilder sb = new StringBuilder("signal_time,direction,outcome,close,stop_loss,take_profit,target_tf,rr,leg_start\n");
        for (SignalPlan s : signals) {
            sb.append(String.format(Locale.ROOT, "%s,%s,%s,%.2f,%s,%s,%s,%s,%s%n",
                    s.signalTime(), s.direction(), s.outcome(), s.close(), num(s.stopLoss()), num(s.takeProfit()),
                    s.target() == null ? "" : s.target().timeframe(),
                    s.rewardToRisk() == null ? "" : String.format(Locale.ROOT, "%.2f", s.rewardToRisk()),
                    s.legStart() == null ? "" : s.legStart()));
        }
        Files.writeString(path, sb);
        log.info("Signals CSV: {}", path);
    }

    public void writeHtml(String title, String settings, TradeStatistics st, List<Trade> trades,
                          List<SignalPlan> signals, Path path) throws IOException {
        Map<SignalOutcome, Long> outcomes = new EnumMap<>(SignalOutcome.class);
        outcomes.putAll(signals.stream().collect(Collectors.groupingBy(SignalPlan::outcome, Collectors.counting())));
        long rrBetween1And15 = signals.stream()
                .filter(s -> s.outcome() == SignalOutcome.RR_TOO_LOW && s.rewardToRisk() >= 1.0)
                .count();
        List<Trade> closed = trades.stream().filter(Trade::closed).toList();

        StringBuilder labels = new StringBuilder("[").append(js(closed.isEmpty() ? null : closed.getFirst().entryTime()));
        StringBuilder equity = new StringBuilder("[").append(fmt(st.initialCapital()));
        for (Trade t : closed) {
            labels.append(',').append(js(t.exitTime()));
            equity.append(',').append(fmt(t.equityAfter()));
        }
        labels.append(']');
        equity.append(']');

        StringBuilder html = new StringBuilder();
        html.append("""
                <!doctype html>
                <html lang="pl"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>%s</title>
                <script src="https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js"></script>
                <style>
                :root{--bg:#f7f7f5;--card:#fff;--fg:#1d1d1b;--muted:#6b6b66;--line:#e3e2dd;--pos:#0f8a5f;--neg:#c2362f;--accent:#2a6fdb}
                @media (prefers-color-scheme:dark){:root{--bg:#141414;--card:#1d1d1d;--fg:#ececea;--muted:#9a9a94;--line:#2e2e2c;--pos:#2fc38a;--neg:#ef6b63;--accent:#6ea0ff}}
                body{margin:0;background:var(--bg);color:var(--fg);font:14px/1.45 -apple-system,BlinkMacSystemFont,"Segoe UI",sans-serif}
                main{max-width:1200px;margin:0 auto;padding:24px 16px}
                h1{font-size:22px;margin:0 0 4px}h2{font-size:16px;margin:32px 0 12px}
                .muted{color:var(--muted)}.pos{color:var(--pos)}.neg{color:var(--neg)}
                .grid{display:grid;grid-template-columns:repeat(auto-fill,minmax(160px,1fr));gap:12px;margin-top:20px}
                .tile{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:12px 14px}
                .tile .k{font-size:12px;color:var(--muted)}.tile .v{font-size:20px;font-weight:600;margin-top:2px;font-variant-numeric:tabular-nums}
                .card{background:var(--card);border:1px solid var(--line);border-radius:10px;padding:16px}
                .chart{position:relative;height:360px}
                .scroll{overflow-x:auto}
                table{border-collapse:collapse;width:100%%;font-variant-numeric:tabular-nums;font-size:13px}
                th,td{padding:6px 10px;border-bottom:1px solid var(--line);text-align:right;white-space:nowrap}
                th:first-child,td:first-child{text-align:left}th{color:var(--muted);font-weight:500}
                .badge{display:inline-block;padding:1px 8px;border-radius:99px;font-size:12px;font-weight:600;color:#fff}
                .b-TP{background:var(--pos)}.b-SL{background:var(--neg)}.b-BE{background:#d98c1f}.b-OPEN_AT_END{background:var(--muted)}
                </style></head><body><main>
                """.formatted(esc(title)));
        html.append("<h1>").append(esc(title)).append("</h1><div class=\"muted\">").append(esc(settings)).append("</div>");

        html.append("<div class=\"grid\">");
        tile(html, "Return", signed(st.returnPct()) + "%", st.returnPct());
        tile(html, "Net P&L", "$" + signed(st.netPnl()), st.netPnl());
        tile(html, "Total trades", String.valueOf(st.trades()), 0);
        tile(html, "Wins / losses / BE", st.wins() + " / " + st.losses() + " / " + st.breakEvens(), 0);
        tile(html, "Win rate", fmt1(st.winRatePct()) + "%", 0);
        tile(html, "Profit factor", fmt2(st.profitFactor()), 0);
        tile(html, "Expectancy", "$" + signed(st.expectancy()), st.expectancy());
        tile(html, "Avg R", signed(st.avgR()) + "R", st.avgR());
        tile(html, "Max DD", "-" + fmt1(st.maxDrawdownPct()) + "%", -1);
        tile(html, "Avg win", "$" + signed(st.avgWin()), 1);
        tile(html, "Avg loss", "$" + signed(st.avgLoss()), -1);
        tile(html, "Avg win / avg loss", fmt2(st.avgWinToAvgLoss()), 0);
        tile(html, "Largest win", "$" + signed(st.largestWin()), 1);
        tile(html, "Largest loss", "$" + signed(st.largestLoss()), -1);
        tile(html, "Win / loss streak", st.maxWinStreak() + " / " + st.maxLossStreak(), 0);
        tile(html, "Avg holding", hours(st.avgHolding()), 0);
        tile(html, "Final capital", "$" + fmt(st.finalCapital()), 0);
        html.append("</div>");

        html.append("<h2>Equity (balance after each closed trade)</h2><div class=\"card\"><div class=\"chart\"><canvas id=\"eq\"></canvas></div></div>");

        html.append("<h2>Signals</h2><div class=\"card scroll\"><table><tr><th>Outcome</th><th>Count</th></tr>");
        outcomes.forEach((k, v) -> html.append("<tr><td>").append(k).append("</td><td>").append(v).append("</td></tr>"));
        html.append("</table><p class=\"muted\">RR_TOO_LOW with reward between 1.0 and the threshold: ")
                .append(rrBetween1And15).append("</p></div>");

        html.append("<h2>Trades</h2><div class=\"card scroll\"><table><tr><th>Entry</th><th>Side</th><th>Type</th><th>Entry px</th><th>SL</th><th>TP</th>"
                + "<th>Target</th><th>Exit</th><th>Exit px</th><th>Result</th><th>R</th><th>P&L</th><th>Equity</th></tr>");
        for (Trade t : trades) {
            html.append(String.format(Locale.ROOT,
                    "<tr><td>%s</td><td>%s</td><td>%s</td><td>%.2f</td><td>%.2f</td><td>%.2f</td><td>%s</td><td>%s</td><td>%.2f</td>"
                            + "<td><span class=\"badge b-%s\">%s</span></td><td class=\"%s\">%+.2f</td><td class=\"%s\">%+.2f</td><td>%.2f</td></tr>",
                    TS.format(t.entryTime()), t.direction(), t.entryType(), t.entryPrice(), t.stopLoss(), t.takeProfit(), t.targetTimeframe(),
                    TS.format(t.exitTime()), t.exitPrice(), t.exitReason(), t.exitReason() == ExitReason.OPEN_AT_END ? "OPEN" : t.exitReason(),
                    cls(t.rMultiple()), t.rMultiple(), cls(t.pnl()), t.pnl(), t.equityAfter()));
        }
        html.append("</table></div>");

        html.append("<h2>No FVG target — for review</h2><div class=\"card scroll\"><table><tr><th>Signal (bar close)</th><th>Close</th><th>SL</th><th>Leg start</th></tr>");
        for (SignalPlan s : signals) {
            if (s.outcome() == SignalOutcome.NO_TARGET) {
                html.append(String.format(Locale.ROOT, "<tr><td>%s</td><td>%.2f</td><td>%.2f</td><td>%s</td></tr>",
                        TS.format(s.signalTime()), s.close(), s.stopLoss(), TS.format(s.legStart())));
            }
        }
        html.append("</table></div>");

        html.append("""
                <script>
                const css = getComputedStyle(document.documentElement);
                new Chart(document.getElementById('eq'), {
                  type: 'line',
                  data: { labels: %s, datasets: [{ data: %s, borderColor: css.getPropertyValue('--accent').trim(),
                    borderWidth: 1.5, pointRadius: 0, fill: false, tension: 0 }] },
                  options: { maintainAspectRatio: false, animation: false, plugins: { legend: { display: false } },
                    interaction: { mode: 'index', intersect: false },
                    scales: { x: { ticks: { maxTicksLimit: 10, color: css.getPropertyValue('--muted').trim() },
                                   grid: { color: css.getPropertyValue('--line').trim() } },
                              y: { ticks: { color: css.getPropertyValue('--muted').trim() },
                                   grid: { color: css.getPropertyValue('--line').trim() } } } }
                });
                </script></main></body></html>
                """.formatted(labels, equity));

        Files.writeString(path, html);
        log.info("HTML report: {}", path);
    }

    private static void tile(StringBuilder html, String key, String value, double sign) {
        html.append("<div class=\"tile\"><div class=\"k\">").append(esc(key)).append("</div><div class=\"v ")
                .append(cls(sign)).append("\">").append(esc(value)).append("</div></div>");
    }

    private static String cls(double v) {
        return v > 0 ? "pos" : v < 0 ? "neg" : "";
    }

    private static String num(Double v) {
        return v == null ? "" : String.format(Locale.ROOT, "%.2f", v);
    }

    private static String fmt(double v) {
        return String.format(Locale.ROOT, "%.2f", v);
    }

    private static String fmt1(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmt2(double v) {
        return Double.isInfinite(v) ? "∞" : String.format(Locale.ROOT, "%.2f", v);
    }

    private static String signed(double v) {
        return String.format(Locale.ROOT, "%+.2f", v);
    }

    private static String hours(Duration d) {
        return String.format(Locale.ROOT, "%.1f h", d.toMinutes() / 60.0);
    }

    private static String js(ZonedDateTime t) {
        return t == null ? "'start'" : "'" + TS.format(t) + "'";
    }

    private static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
