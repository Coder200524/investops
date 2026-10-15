package com.investops.trade.controller;

import com.investops.common.dto.ApiResponse;
import com.investops.common.dto.PagedResponse;
import com.investops.trade.dto.ImportBatchDto;
import com.investops.trade.dto.TradeImportResponse;
import com.investops.trade.dto.TradeRecordDto;
import com.investops.trade.entity.SourceSystem;
import com.investops.trade.entity.TradeSide;
import com.investops.trade.service.TradeImportService;
import com.investops.trade.service.TradeQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/trades")
@RequiredArgsConstructor
public class TradeController {

    private final TradeImportService tradeImportService;
    private final TradeQueryService tradeQueryService;

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<TradeImportResponse>> importTrades(
            @RequestParam("file") MultipartFile file,
            @RequestParam("sourceSystem") String sourceSystem) {

        TradeImportResponse response = tradeImportService.importTrades(file, sourceSystem, null);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResponse<TradeRecordDto>>> getTrades(
            @RequestParam(required = false) String sourceSystem,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate tradeDateTo,
            @RequestParam(required = false) String symbol,
            @RequestParam(required = false) String side,
            @PageableDefault(size = 25, sort = "tradeDate", direction = Sort.Direction.DESC) Pageable pageable) {

        SourceSystem sourceSystemEnum = sourceSystem != null && !sourceSystem.isBlank()
                ? SourceSystem.fromString(sourceSystem)
                : null;
        TradeSide tradeSideEnum = side != null && !side.isBlank()
                ? TradeSide.fromString(side)
                : null;

        PagedResponse<TradeRecordDto> trades = tradeQueryService.findTrades(
                sourceSystemEnum, tradeDateFrom, tradeDateTo, symbol, tradeSideEnum, pageable);

        return ResponseEntity.ok(ApiResponse.ok(trades));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<TradeRecordDto>> getTradeById(@PathVariable UUID id) {
        TradeRecordDto trade = tradeQueryService.getTradeById(id);
        return ResponseEntity.ok(ApiResponse.ok(trade));
    }

    @GetMapping("/batches/{batchId}")
    public ResponseEntity<ApiResponse<ImportBatchDto>> getBatchById(@PathVariable UUID batchId) {
        ImportBatchDto batch = tradeQueryService.getBatchById(batchId);
        return ResponseEntity.ok(ApiResponse.ok(batch));
    }
}
