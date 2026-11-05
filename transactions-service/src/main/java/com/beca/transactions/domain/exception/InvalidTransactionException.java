package com.beca.transactions.domain.exception;

public class InvalidTransactionException extends IllegalArgumentException{
    public InvalidTransactionException(String message){
        super(message);
    }
}
