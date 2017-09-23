package utils;

/**
 * Created by Vidu Wijayaweera on 8/31/2017.
 */
public class APISubscriptionException extends Exception {

    APISubscriptionException(String msg) {
        super(msg);
    }

    APISubscriptionException(String msg, Throwable e) {
        super(msg, e);
    }

}
