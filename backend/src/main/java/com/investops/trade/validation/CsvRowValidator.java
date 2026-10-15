package com.investops.trade.validation;

import com.investops.trade.dto.RowErrorDto;
import com.investops.trade.entity.TradeSide;
import com.investops.trade.entity.TradeStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

@Component
public class CsvRowValidator {

    public static final String[] EXPECTED_HEADERS = {
            "trade_id", "trade_date", "settlement_date", "symbol", "side", "quantity", "price", "counterparty", "status"
    };

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidationResult {
        private boolean valid;
        private ValidatedTradeRow row;
        private List<RowErrorDto> errors;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ValidatedTradeRow {
        private String externalTradeId;
        private LocalDate tradeDate;
        private LocalDate settlementDate;
        private String symbol;
        private TradeSide side;
        private BigDecimal quantity;
        private BigDecimal price;
        private String counterparty;
        private TradeStatus status;
    }

    public ValidationResult validateRow(CSVRecord record, int rowNumber) {
        List<RowErrorDto> errors = new ArrayList<>();

        // 1. Validate trade_id
        String tradeId = getField(record, "trade_id");
        if (tradeId == null || tradeId.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "trade_id", "trade_id is required", tradeId));
        } else if (tradeId.trim().length() > 50) {
            errors.add(new RowErrorDto(rowNumber, "trade_id", "trade_id exceeds maximum length of 50", tradeId));
        }

        // 2. Validate trade_date
        String tradeDateStr = getField(record, "trade_date");
        LocalDate tradeDate = null;
        if (tradeDateStr == null || tradeDateStr.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "trade_date", "trade_date is required", tradeDateStr));
        } else {
            try {
                tradeDate = LocalDate.parse(tradeDateStr.trim());
            } catch (DateTimeParseException e) {
                errors.add(new RowErrorDto(rowNumber, "trade_date", "trade_date must be formatted as YYYY-MM-DD", tradeDateStr));
            }
        }

        // 3. Validate settlement_date
        String settlementDateStr = getField(record, "settlement_date");
        LocalDate settlementDate = null;
        if (settlementDateStr == null || settlementDateStr.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "settlement_date", "settlement_date is required", settlementDateStr));
        } else {
            try {
                settlementDate = LocalDate.parse(settlementDateStr.trim());
                if (tradeDate != null && settlementDate.isBefore(tradeDate)) {
                    errors.add(new RowErrorDto(rowNumber, "settlement_date", "settlement_date cannot precede trade_date", settlementDateStr));
                }
            } catch (DateTimeParseException e) {
                errors.add(new RowErrorDto(rowNumber, "settlement_date", "settlement_date must be formatted as YYYY-MM-DD", settlementDateStr));
            }
        }

        // 4. Validate symbol
        String symbol = getField(record, "symbol");
        if (symbol == null || symbol.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "symbol", "symbol is required", symbol));
        } else if (symbol.trim().length() > 20) {
            errors.add(new RowErrorDto(rowNumber, "symbol", "symbol exceeds maximum length of 20", symbol));
        }

        // 5. Validate side
        String sideStr = getField(record, "side");
        TradeSide side = null;
        if (sideStr == null || sideStr.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "side", "side is required (BUY or SELL)", sideStr));
        } else {
            try {
                side = TradeSide.fromString(sideStr);
            } catch (IllegalArgumentException e) {
                errors.add(new RowErrorDto(rowNumber, "side", e.getMessage(), sideStr));
            }
        }

        // 6. Validate quantity
        String quantityStr = getField(record, "quantity");
        BigDecimal quantity = null;
        if (quantityStr == null || quantityStr.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "quantity", "quantity is required", quantityStr));
        } else {
            try {
                quantity = new BigDecimal(quantityStr.trim());
                if (quantity.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new RowErrorDto(rowNumber, "quantity", "quantity must be strictly positive", quantityStr));
                }
            } catch (NumberFormatException e) {
                errors.add(new RowErrorDto(rowNumber, "quantity", "quantity must be a valid number", quantityStr));
            }
        }

        // 7. Validate price
        String priceStr = getField(record, "price");
        BigDecimal price = null;
        if (priceStr == null || priceStr.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "price", "price is required", priceStr));
        } else {
            try {
                price = new BigDecimal(priceStr.trim());
                if (price.compareTo(BigDecimal.ZERO) <= 0) {
                    errors.add(new RowErrorDto(rowNumber, "price", "price must be strictly positive", priceStr));
                }
            } catch (NumberFormatException e) {
                errors.add(new RowErrorDto(rowNumber, "price", "price must be a valid number", priceStr));
            }
        }

        // 8. Validate counterparty
        String counterparty = getField(record, "counterparty");
        if (counterparty == null || counterparty.trim().isEmpty()) {
            errors.add(new RowErrorDto(rowNumber, "counterparty", "counterparty is required", counterparty));
        } else if (counterparty.trim().length() > 100) {
            errors.add(new RowErrorDto(rowNumber, "counterparty", "counterparty exceeds maximum length of 100", counterparty));
        }

        // 9. Status (optional in CSV)
        String statusStr = getField(record, "status");
        TradeStatus status = TradeStatus.fromString(statusStr);

        if (!errors.isEmpty()) {
            return ValidationResult.builder()
                    .valid(false)
                    .errors(errors)
                    .build();
        }

        ValidatedTradeRow validatedRow = ValidatedTradeRow.builder()
                .externalTradeId(tradeId != null ? tradeId.trim() : null)
                .tradeDate(tradeDate)
                .settlementDate(settlementDate)
                .symbol(symbol != null ? symbol.trim().toUpperCase() : null)
                .side(side)
                .quantity(quantity)
                .price(price)
                .counterparty(counterparty != null ? counterparty.trim().toUpperCase() : null)
                .status(status)
                .build();

        return ValidationResult.builder()
                .valid(true)
                .row(validatedRow)
                .errors(List.of())
                .build();
    }

    private String getField(CSVRecord record, String name) {
        if (record.isMapped(name)) {
            return record.get(name);
        }
        // Case-insensitive header lookup fallback
        for (String header : record.getParser().getHeaderNames()) {
            if (header.equalsIgnoreCase(name)) {
                return record.get(header);
            }
        }
        return null;
    }
}
