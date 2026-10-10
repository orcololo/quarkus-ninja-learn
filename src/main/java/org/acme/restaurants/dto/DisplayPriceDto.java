package org.acme.restaurants.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record DisplayPriceDto(BigDecimal amount, String currency, LocalDate rateDate) {
}
