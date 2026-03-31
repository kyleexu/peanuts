package com.ganten.peanuts.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class DecimalLogFormatter {

    private DecimalLogFormatter() {}

    public static String p4(BigDecimal value) {
        if (value == null) {
            return "null";
        }
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}

