package com.jbooktrader.platform.backtest;


import com.jbooktrader.platform.chart.*;
import com.jbooktrader.platform.indicator.*;
import com.jbooktrader.platform.marketbar.MarketData;
import com.jbooktrader.platform.marketbar.Snapshot;
import com.jbooktrader.platform.model.*;
import com.jbooktrader.platform.model.ModelListener.*;
import com.jbooktrader.platform.schedule.*;
import com.jbooktrader.platform.strategy.*;

import java.util.*;

/**
 * This class is responsible for running the strategy against historical market data
 *
 * @author Eugene Kononov
 */
public class BackTester {
    private final Strategy strategy;
    private final BackTestBookFileReader backTestFileReader;
    private final BackTestDialog backTestDialog;

    public BackTester(Strategy strategy, BackTestBookFileReader backTestFileReader, BackTestDialog backTestDialog) {
        this.strategy = strategy;
        this.backTestFileReader = backTestFileReader;
        this.backTestDialog = backTestDialog;
    }

    public void execute() throws JBookTraderException {
        List<Snapshot> snapshots = backTestFileReader.load(backTestDialog);

        MarketData marketBook = strategy.getMarket();
        IndicatorManager indicatorManager = strategy.getIndicatorManager();
        strategy.getPerformanceManager().createPerformanceChartData(backTestDialog.getBarSize(), indicatorManager.getIndicators());

        List<Indicator> indicators = indicatorManager.getIndicators();
        TradingSchedule tradingSchedule = strategy.getTradingSchedule();
        PerformanceChartData performanceChartData = strategy.getPerformanceManager().getPerformanceChartData();

        long snapshotsCount = snapshots.size();
        for (int count = 0; count < snapshotsCount; count++) {
            Snapshot marketSnapshot = snapshots.get(count);
            marketBook.setSnapshot(marketSnapshot);
            performanceChartData.update(marketSnapshot);
            indicatorManager.updateIndicators(strategy);
            long instant = marketSnapshot.getTime();

            //TODO: This when-to-trade logic (ruling out gaps in the market), again, seems like
            //TODO: it should belong in the strategy itself.  Investigate/refactor this.
            boolean isInSchedule = tradingSchedule.contains(instant);
            if (count < snapshotsCount - 1) {
                isInSchedule = isInSchedule && !marketBook.isGapping(snapshots.get(count + 1));
            }

            strategy.processInstant(isInSchedule);
            if (indicatorManager.hasValidIndicators(strategy)) {
                performanceChartData.update(indicators, instant);
            }

            if (count % 100000 == 0) {
                backTestDialog.setProgress(count, snapshotsCount, "Running back test");
                if (backTestDialog.isCancelled()) {
                    break;
                }
            }
        }

        if (!backTestDialog.isCancelled()) {
            // go flat at the end of the test period to finalize the run
            strategy.closePosition();
            Dispatcher.getInstance().fireModelChanged(Event.StrategyUpdate, strategy);
        }
    }
}
