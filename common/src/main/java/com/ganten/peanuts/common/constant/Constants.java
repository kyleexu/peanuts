package com.ganten.peanuts.common.constant;

import java.util.Arrays;
import java.util.List;

import io.aeron.CommonContext;

public class Constants {

    public static final List<Integer> multiplierList = Arrays.asList(1, 5, 10, 50, 100);

    public static final long ACCOUNT_LOCK_TIMEOUT_MS = 800L; // 800ms

    public static final String AERON_CHANNEL = "aeron:ipc";
    public static final boolean AERON_ENABLED = true;
    public static final boolean AERON_LAUNCH_EMBEDDED_DRIVER = true;
    public static final String AERON_DIRECTORY = CommonContext.getAeronDirectoryName();
    public static final int AERON_FRAGMENT_LIMIT = 10;
    public static final int AERON_FRAGMENT_LIMIT_LOCK_RESPONSE = 20;
    public static final int AERON_FRAGMENT_LIMIT_MARKET = 100;

    public static final int AERON_STREAM_ID_LOCK_RESPONSE = 2102;
    public static final int AERON_STREAM_ID_LOCK_REQUEST = 2101;
    public static final int AERON_STREAM_ID_TRADE = 2003;
    public static final int AERON_STREAM_ID_ACCOUNT_TRADE = 2103;
    public static final int AERON_STREAM_ID_ORDER_BOOK = 2004;
    public static final int AERON_STREAM_ID_ORDER = 2001;
    public static final int AERON_STREAM_ID_EXECUTION_REPORT = 2002;
}
