package it.polimi.bsnwebapp.exception;

/**
 * Classe per la gestione delle eccezioni con status code 403.
 * Estende {@link RuntimeException}, superclasse delle eccezioni
 * che possono essere lanciate durante il l'esecuzione della JVM.
 */
public class ConflictException extends RuntimeException{

    public ConflictException(String message) {
        super(message);
    }
}