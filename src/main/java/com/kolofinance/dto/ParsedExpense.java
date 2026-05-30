package com.kolofinance.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedExpense {
    private Long amount;
    private String description;
    private String category;
    private boolean parsed;
}
