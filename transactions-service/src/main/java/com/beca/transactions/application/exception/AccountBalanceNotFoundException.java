package com.beca.transactions.application.exception;

public class AccountBalanceNotFoundException extends RuntimeException{
    public AccountBalanceNotFoundException(Long userId){
        super("Conta de saldo do usuário " + userId + " não encontrada");
    }
}
