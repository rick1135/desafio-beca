package com.beca.transactions.domain.model;

import java.math.BigDecimal;

public record AccountBalance(
        Long userId,
        BigDecimal openingBalance,
        BigDecimal dailyLimit,
        BigDecimal transactionLimit
) {
     public AccountBalance{
         if(userId==null || userId<=0) throw new IllegalArgumentException("ID do usuário inválido");
         requireNonNegative(openingBalance, "Saldo inicial");
         requireNonNegative(dailyLimit, "Limite diário");
         requireNonNegative(transactionLimit, "Limite por transação");
     }
     private static void requireNonNegative(BigDecimal value, String field){
         if(value==null || value.signum()<0) throw new IllegalArgumentException(field+" deve ser maior ou igual a zero");
     }
}
