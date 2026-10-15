package com.investops.trade.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowErrorDto {
    private int row;
    private String field;
    private String message;
    private String rawValue;
}
