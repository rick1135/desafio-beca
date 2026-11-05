package com.beca.transactions.application.exception;

public class TransactionNotFoundException extends RuntimeException{
    public TransactionNotFoundException(Long id){
        super("Transação " + id + " não encontrada");
    }
}
