package com.beca.transactions.application.exception;

public class ForbiddenTransactionAccessException extends RuntimeException {
    public ForbiddenTransactionAccessException() {
        super("Acesso negado à transação de outro usuário");
    }
}
