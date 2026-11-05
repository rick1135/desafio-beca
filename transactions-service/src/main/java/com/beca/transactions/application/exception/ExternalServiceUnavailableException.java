package com.beca.transactions.application.exception;

public class ExternalServiceUnavailableException extends RuntimeException{
    public ExternalServiceUnavailableException(String service, Throwable cause){
        super("Falha ao consultar " + service, cause);
    }
}
