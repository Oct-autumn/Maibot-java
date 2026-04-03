package org.maibot.sdk.exceptions;

public class NamespaceAlreadyExist extends UnignorableException {
    public NamespaceAlreadyExist(String msg, Object... args) {
        super(msg, args);
    }
}
