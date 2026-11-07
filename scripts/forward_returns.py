#!/usr/bin/env python3
"""
Forward returns of every signal a DoubleDivergenceBacktest run exported, against random entries.

Usage:
    python3 scripts/forward_returns.py <csv> <pine> <bar-hours>

Arguments:
    csv        candle file from data/ (TradingView export: time, open, high, low, close, ...)
    pine       the .pine file DoubleDivergenceBacktest wrote for the same asset and timeframe
    bar-hours  bar length in hours (4 for H4, 1 for H1, 12 for H12)

Example:
    mvn exec:java -Dexec.mainClass=pl.tradeengine.backtest.DoubleDivergenceBacktest -Dexec.args="H12 tao"
    python3 scripts/forward_returns.py data/tao_h12.csv output/tao_double_divergence_h12.pine 12

Run the backtest first: output/ is not in git.

Output: one header line (signal count and date range), then one line per horizon (1, 3, 5, 10
days) with the number of usable signals, mean signed return, hit rate, the random baseline's
mean, p, and the mean split into LONG and SHORT signals.

Standard library only. The random baseline is seeded with SEED (1), so every run prints the same
p-values.

Entry is the OPEN of the bar the signal is stamped on (the bar that opens when the live alert
fires), exit the CLOSE n bars later. Returns are signed by direction, with no fees and no slippage.

The baseline keeps the signals' own LONG/SHORT mix and puts each one on a random bar of the same
period, 2000 times. p is the share of those random runs whose mean return was at least the
signals' mean. It treats signals as independent, which clustered divergences are not, so it is
optimistic.
"""
import csv
import datetime as dt
import random
import re
import statistics as st
import sys

HORIZONS_DAYS = (1, 3, 5, 10)
RANDOM_RUNS = 2000
SEED = 1  # fixed so the p-values are reproducible


def load_bars(path):
    bars = []
    with open(path) as f:
        for row in list(csv.reader(f))[1:]:
            t = row[0]
            ts = int(t) if t.isdigit() else int(dt.datetime.fromisoformat(t).timestamp())
            bars.append((ts * 1000, float(row[1]), float(row[4])))
    return bars


def load_signals(path):
    text = open(path).read()
    longs, shorts = text.split("// SHORT timestamps")[:2]
    times = lambda s: [int(x) for x in re.findall(r"array\.push\(\w+, (\d+)\)", s)]
    return [(t, 1) for t in times(longs)] + [(t, -1) for t in times(shorts)]


def signed_return(bars, i, n, direction):
    return direction * (bars[i + n - 1][2] / bars[i][1] - 1)


def main(csv_path, pine_path, bar_hours):
    bars = load_bars(csv_path)
    index = {b[0]: i for i, b in enumerate(bars)}
    signals = [(index[t], d) for t, d in load_signals(pine_path) if t in index]
    day = lambda ms: dt.datetime.fromtimestamp(ms / 1000, dt.timezone.utc).date()
    first = min(i for i, _ in signals)
    print(f"{len(signals)} signals, {day(bars[first][0])} .. {day(bars[-1][0])}")

    rng = random.Random(SEED)
    for days in HORIZONS_DAYS:
        n = round(days * 24 / bar_hours)
        usable = [(i, d) for i, d in signals if i + n < len(bars)]
        rets = [signed_return(bars, i, n, d) for i, d in usable]
        mean = st.mean(rets)
        pool = range(first, len(bars) - n)
        baseline = [st.mean(signed_return(bars, rng.choice(pool), n, d) for _, d in usable)
                    for _ in range(RANDOM_RUNS)]
        p = sum(b >= mean for b in baseline) / RANDOM_RUNS
        by_dir = {d: [r for r, (_, dd) in zip(rets, usable) if dd == d] for d in (1, -1)}
        print(f"{days:>3}d ({n:>3} bars)  n={len(rets):<4}"
              f" mean {mean * 100:+6.2f}%  hit {sum(r > 0 for r in rets) / len(rets):4.0%}"
              f"  random {st.mean(baseline) * 100:+6.2f}%  p={p:.2f}"
              f"  | LONG {st.mean(by_dir[1]) * 100:+6.2f}%  SHORT {st.mean(by_dir[-1]) * 100:+6.2f}%")


if __name__ == "__main__":
    if len(sys.argv) != 4:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2], float(sys.argv[3]))
