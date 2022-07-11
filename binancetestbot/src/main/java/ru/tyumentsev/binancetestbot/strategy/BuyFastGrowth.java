package ru.tyumentsev.binancetestbot.strategy;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.binance.api.client.domain.market.TickerStatistics;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import ru.tyumentsev.binancetestbot.cache.MarketData;
import ru.tyumentsev.binancetestbot.service.MarketInfo;

@Service
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class BuyFastGrowth {

    /*
     * this strategy will get all price changes of pairs to USDT
     * and buy this coin if price changes more then 10% for the last 3 hours
     */

    @Autowired
    MarketInfo marketInfo;
    @Autowired
    MarketData marketData;

    final String WINDOW_SIZE = "3h";
    final String QUERY_SYMBOLS_BEGIN = "[", QUERY_SYMBOLS_END = "]";
    final String DELIMETER = "\"";
    final int QUERY_SYMBOLS_PART_SIZE = 40;

    StringBuilder symbolsParameterBuilder = new StringBuilder(); // build query in format, that accepts by binance API.

    public List<TickerStatistics> addPairsToBuy(String asset) {
        // get assets that paired to USDT.
        List<String> pairs = marketData.getAvailablePairs(asset);

        int qtyOfParts = 0; // quantity of groups of pairs, need to split because of binance API limits.
        if (pairs.size() > QUERY_SYMBOLS_PART_SIZE) {
            qtyOfParts = pairs.size() % QUERY_SYMBOLS_PART_SIZE == 0 ? pairs.size() / QUERY_SYMBOLS_PART_SIZE
                    : pairs.size() / QUERY_SYMBOLS_PART_SIZE + 1;
        } else if (!pairs.isEmpty()) {
            qtyOfParts = 1;
        }

        List<TickerStatistics> accumulatedResponses = new ArrayList<>(); // accumulate here responses from binance API.
        for (int i = 0, fromIndex = 0, toIndex = 0; i < qtyOfParts; i++) {
            if (i == qtyOfParts - 1) { // last iteration, need to define last index.
                fromIndex = toIndex;
                toIndex = pairs.size();
            } else {
                fromIndex = QUERY_SYMBOLS_PART_SIZE * i;
                toIndex = QUERY_SYMBOLS_PART_SIZE * i + QUERY_SYMBOLS_PART_SIZE;
            }
            accumulatedResponses.addAll(getPartOfStatistics(pairs, fromIndex, toIndex));
        }

        marketData.loadPairsToBuy(accumulatedResponses);
        
        return new ArrayList<>(marketData.getPairsToBuy());
    }

    private List<TickerStatistics> getPartOfStatistics(List<String> pairs, int fromIndex, int toIndex) {
        symbolsParameterBuilder.append(QUERY_SYMBOLS_BEGIN);

        for (String pair : pairs.subList(fromIndex, toIndex)) {
            symbolsParameterBuilder.append(DELIMETER);
            symbolsParameterBuilder.append(pair);
            symbolsParameterBuilder.append(DELIMETER);
            symbolsParameterBuilder.append(",");
        }
        symbolsParameterBuilder.deleteCharAt(symbolsParameterBuilder.length() - 1); // delete "," in last line.
        symbolsParameterBuilder.append(QUERY_SYMBOLS_END); // result must be in format "["BTCUSDT","BNBUSDT"]".

        List<TickerStatistics> statistics = marketInfo
                .getAllWindowPriceChange(symbolsParameterBuilder.toString(), WINDOW_SIZE);

        symbolsParameterBuilder.delete(0, symbolsParameterBuilder.capacity());

        return statistics.stream()
                .filter(tickerStatistics -> Double.parseDouble(tickerStatistics.getPriceChangePercent()) > 5)
                .collect(Collectors.toList());
    }
}
