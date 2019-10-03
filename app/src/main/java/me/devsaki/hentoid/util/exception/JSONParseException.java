package me.devsaki.hentoid.util.exception;

/**
 * Created by avluis on 06/12/2016.
 * Attribute Exception
 */
public class JSONParseException extends Exception {

    public JSONParseException(String message) {
        super(message);
    }

    public JSONParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
