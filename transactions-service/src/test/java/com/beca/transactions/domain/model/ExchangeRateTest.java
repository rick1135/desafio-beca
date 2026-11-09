package com.beca.transactions.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ExchangeRateTest {
    @Test
    @DisplayName("converte valor em reais usando a cotação de venda com arredondamento HALF_UP")
    void testConvertFromBrl_WhenAmountIsValid_ShouldDivideUsingHalfUp() {
        // given
        var rate = new ExchangeRate(ExchangeCurrency.USD, new BigDecimal("5.5013"),
                LocalDate.parse("2026-11-06"), LocalDateTime.parse("2026-11-06T13:06:13"),
                "FECHAMENTO PTAX");

        // when
        BigDecimal converted = rate.convertFromBrl(new BigDecimal("100.00"));

        // then
        assertEquals(new BigDecimal("18.18"), converted);
    }
}
