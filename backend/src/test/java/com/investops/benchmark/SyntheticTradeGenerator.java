package com.investops.benchmark;

import com.investops.trade.entity.ImportBatch;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeRecord;
import com.investops.trade.entity.TradeSide;
import com.investops.trade.entity.TradeStatus;
import lombok.Builder;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;

/**
 * Deterministic synthetic trade dataset generator for reconciliation benchmarking.
 * Generates configurable distributions of exact matches, quantity mismatches, price differences,
 * settlement date mismatches, missing trades from either source, and duplicate candidate keys.
 * Uses a fixed random seed for 100% reproducible benchmark datasets.
 */
public class SyntheticTradeGenerator {

    private static final String[] TICKERS = {
            "NVDA", "AAPL", "MSFT", "AMZN", "GOOGL", "META", "TSLA", "SPY", "QQQ", "JPM",
            "GS", "LLY", "AVGO", "WMT", "V", "MA", "COST", "HD", "UNH", "XOM", "BAC"
    };

    private static final double[] BASE_PRICES = {
            138.25, 228.50, 422.10, 188.40, 166.75, 584.20, 218.60, 582.40, 492.30, 222.15,
            512.80, 915.00, 178.90, 80.45, 282.10, 495.30, 902.50, 388.20, 585.60, 124.30, 42.10
    };

    private static final String[] COUNTERPARTIES = {
            "GOLDMAN_SACHS", "JPMORGAN", "MORGAN_STANLEY", "CITIGROUP",
            "BARCLAYS", "BANK_OF_AMERICA", "BNP_PARIBAS", "UBS"
    };

    @Getter
    @Builder
    public static class DatasetConfig {
        private final int countPerSource;
        @Builder.Default
        private final long seed = 42L;
        @Builder.Default
        private final double exactMatchRatio = 0.70;
        @Builder.Default
        private final double quantityMismatchRatio = 0.08;
        @Builder.Default
        private final double priceMismatchRatio = 0.08;
        @Builder.Default
        private final double settlementDateMismatchRatio = 0.06;
        @Builder.Default
        private final double missingInSourceARatio = 0.03;
        @Builder.Default
        private final double missingInSourceBRatio = 0.03;
        @Builder.Default
        private final double duplicateRatio = 0.02;

        public static DatasetConfig of(int countPerSource) {
            return DatasetConfig.builder().countPerSource(countPerSource).build();
        }

        public static DatasetConfig of(int countPerSource, long seed) {
            return DatasetConfig.builder().countPerSource(countPerSource).seed(seed).build();
        }
    }

    @Getter
    @Builder
    public static class SyntheticDataset {
        private final List<TradeRecord> tradesA;
        private final List<TradeRecord> tradesB;
        private final int targetCount;
        private final int exactMatchCount;
        private final int quantityMismatchCount;
        private final int priceMismatchCount;
        private final int settlementDateMismatchCount;
        private final int missingInSourceACount;
        private final int missingInSourceBCount;
        private final int duplicateGroupCount;
        private final long seed;
    }

    /**
     * Generates a deterministic synthetic trade dataset for benchmarking.
     */
    public static SyntheticDataset generate(DatasetConfig config) {
        Random random = new Random(config.getSeed());
        int target = config.getCountPerSource();

        int exactCount = (int) Math.round(target * config.getExactMatchRatio());
        int qtyCount = (int) Math.round(target * config.getQuantityMismatchRatio());
        int priceCount = (int) Math.round(target * config.getPriceMismatchRatio());
        int dateCount = (int) Math.round(target * config.getSettlementDateMismatchRatio());
        int missingBCount = (int) Math.round(target * config.getMissingInSourceBRatio());
        int missingACount = (int) Math.round(target * config.getMissingInSourceARatio());
        int dupCount = Math.max(1, (int) Math.round(target * config.getDuplicateRatio()));

        // Adjust exactCount so that tradesA total approximately equals target
        int currentTotalA = exactCount + qtyCount + priceCount + dateCount + missingBCount + (dupCount * 2);
        if (currentTotalA < target) {
            exactCount += (target - currentTotalA);
        } else if (currentTotalA > target && exactCount > (currentTotalA - target)) {
            exactCount -= (currentTotalA - target);
        }

        List<TradeRecord> tradesA = new ArrayList<>(target + 50);
        List<TradeRecord> tradesB = new ArrayList<>(target + 50);

        ImportBatch batchA = ImportBatch.builder().id(UUID.fromString("11111111-1111-1111-1111-111111111111")).build();
        ImportBatch batchB = ImportBatch.builder().id(UUID.fromString("22222222-2222-2222-2222-222222222222")).build();

        LocalDate baseTradeDate = LocalDate.of(2026, 10, 15);
        LocalDate baseSettlDate = LocalDate.of(2026, 10, 17);

        int seq = 1;

        // 1. Exact Matches (1:1 with identical attributes; some with leading zeros in one source to test normalization)
        for (int i = 0; i < exactCount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qty = BigDecimal.valueOf((random.nextInt(100) + 1) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            double basePrice = BASE_PRICES[tickerIdx] + (random.nextDouble() * 2.0 - 1.0);
            BigDecimal price = BigDecimal.valueOf(basePrice).setScale(8, RoundingMode.HALF_UP);
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];

            String externalIdA = String.format("TRD-EX-%06d", seq);
            // In 20% of cases, represent B with unpadded numeric key to test key normalization
            String externalIdB = (i % 5 == 0) ? String.format("TRD-EX-%d", seq) : externalIdA;

            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, externalIdA, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchA));
            tradesB.add(buildTrade(SourceSystem.SYSTEM_B, externalIdB, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchB));
            seq++;
        }

        // 2. Quantity Mismatches (different quantity)
        for (int i = 0; i < qtyCount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qtyA = BigDecimal.valueOf((random.nextInt(50) + 10) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            BigDecimal qtyB = qtyA.subtract(BigDecimal.valueOf((random.nextInt(5) + 1) * 50L)).setScale(8, RoundingMode.UNNECESSARY);
            if (qtyB.compareTo(BigDecimal.ZERO) <= 0) {
                qtyB = qtyA.add(BigDecimal.valueOf(100)).setScale(8, RoundingMode.UNNECESSARY);
            }
            BigDecimal price = BigDecimal.valueOf(BASE_PRICES[tickerIdx]).setScale(8, RoundingMode.HALF_UP);
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            String externalId = String.format("TRD-QTY-%06d", seq);

            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, externalId, symbol, side, qtyA, price, cp, baseTradeDate, baseSettlDate, batchA));
            tradesB.add(buildTrade(SourceSystem.SYSTEM_B, externalId, symbol, side, qtyB, price, cp, baseTradeDate, baseSettlDate, batchB));
            seq++;
        }

        // 3. Price Differences (exceeding standard 0.01 tolerance)
        for (int i = 0; i < priceCount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qty = BigDecimal.valueOf((random.nextInt(50) + 10) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            double basePrice = BASE_PRICES[tickerIdx];
            BigDecimal priceA = BigDecimal.valueOf(basePrice).setScale(8, RoundingMode.HALF_UP);
            BigDecimal priceB = BigDecimal.valueOf(basePrice + 0.25).setScale(8, RoundingMode.HALF_UP); // 25 cents diff > 0.01 tolerance
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            String externalId = String.format("TRD-PRC-%06d", seq);

            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, externalId, symbol, side, qty, priceA, cp, baseTradeDate, baseSettlDate, batchA));
            tradesB.add(buildTrade(SourceSystem.SYSTEM_B, externalId, symbol, side, qty, priceB, cp, baseTradeDate, baseSettlDate, batchB));
            seq++;
        }

        // 4. Settlement Date Mismatches (settlement date differs by 1 or 2 days)
        for (int i = 0; i < dateCount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qty = BigDecimal.valueOf((random.nextInt(50) + 10) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            BigDecimal price = BigDecimal.valueOf(BASE_PRICES[tickerIdx]).setScale(8, RoundingMode.HALF_UP);
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            String externalId = String.format("TRD-DAT-%06d", seq);

            LocalDate settlA = baseSettlDate;
            LocalDate settlB = baseSettlDate.plusDays(1);

            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, externalId, symbol, side, qty, price, cp, baseTradeDate, settlA, batchA));
            tradesB.add(buildTrade(SourceSystem.SYSTEM_B, externalId, symbol, side, qty, price, cp, baseTradeDate, settlB, batchB));
            seq++;
        }

        // 5. Missing in Source B (present in Source A only)
        for (int i = 0; i < missingBCount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qty = BigDecimal.valueOf((random.nextInt(50) + 10) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            BigDecimal price = BigDecimal.valueOf(BASE_PRICES[tickerIdx]).setScale(8, RoundingMode.HALF_UP);
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            String externalId = String.format("TRD-MSA-%06d", seq);

            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, externalId, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchA));
            seq++;
        }

        // 6. Missing in Source A (present in Source B only)
        for (int i = 0; i < missingACount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qty = BigDecimal.valueOf((random.nextInt(50) + 10) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            BigDecimal price = BigDecimal.valueOf(BASE_PRICES[tickerIdx]).setScale(8, RoundingMode.HALF_UP);
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            String externalId = String.format("TRD-MSB-%06d", seq);

            tradesB.add(buildTrade(SourceSystem.SYSTEM_B, externalId, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchB));
            seq++;
        }

        // 7. Duplicate candidate keys (different raw external_trade_id but identical normalized business key)
        for (int i = 0; i < dupCount; i++) {
            int tickerIdx = random.nextInt(TICKERS.length);
            String symbol = TICKERS[tickerIdx];
            TradeSide side = random.nextBoolean() ? TradeSide.BUY : TradeSide.SELL;
            BigDecimal qty = BigDecimal.valueOf((random.nextInt(50) + 10) * 100L).setScale(8, RoundingMode.UNNECESSARY);
            BigDecimal price = BigDecimal.valueOf(BASE_PRICES[tickerIdx]).setScale(8, RoundingMode.HALF_UP);
            String cp = COUNTERPARTIES[random.nextInt(COUNTERPARTIES.length)];
            String dupKeyPadded = String.format("TRD-DUP-%06d", seq);
            String dupKeyUnpadded = String.format("TRD-DUP-%d", seq);

            // Add 2 candidates in Source A (distinct in DB, identical normalized key)
            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, dupKeyPadded, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchA));
            tradesA.add(buildTrade(SourceSystem.SYSTEM_A, dupKeyUnpadded, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchA));

            // Add 1 candidate in Source B
            tradesB.add(buildTrade(SourceSystem.SYSTEM_B, dupKeyPadded, symbol, side, qty, price, cp, baseTradeDate, baseSettlDate, batchB));
            seq++;
        }

        return SyntheticDataset.builder()
                .tradesA(tradesA)
                .tradesB(tradesB)
                .targetCount(target)
                .exactMatchCount(exactCount)
                .quantityMismatchCount(qtyCount)
                .priceMismatchCount(priceCount)
                .settlementDateMismatchCount(dateCount)
                .missingInSourceACount(missingACount)
                .missingInSourceBCount(missingBCount)
                .duplicateGroupCount(dupCount)
                .seed(config.getSeed())
                .build();
    }

    private static TradeRecord buildTrade(SourceSystem source, String externalId, String symbol,
                                          TradeSide side, BigDecimal quantity, BigDecimal price,
                                          String counterparty, LocalDate tradeDate, LocalDate settlDate,
                                          ImportBatch batch) {
        return TradeRecord.builder()
                .id(UUID.randomUUID())
                .externalTradeId(externalId)
                .sourceSystem(source)
                .symbol(symbol)
                .side(side)
                .quantity(quantity)
                .price(price)
                .counterparty(counterparty)
                .status(TradeStatus.VALIDATED)
                .tradeDate(tradeDate)
                .settlementDate(settlDate)
                .importBatch(batch)
                .build();
    }
}
