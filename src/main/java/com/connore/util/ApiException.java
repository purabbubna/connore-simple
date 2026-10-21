package com.connore.util;

public class ApiException extends RuntimeException {
    public final int status;
    public final String code;
    public ApiException(int status, String code) { super(code); this.status=status; this.code=code; }
    public static void require(boolean condition, int status, String code) {
        if (!condition) throw new ApiException(status, code);
    }
}
