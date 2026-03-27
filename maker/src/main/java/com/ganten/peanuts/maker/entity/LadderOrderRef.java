package com.ganten.peanuts.maker.entity;

import com.ganten.peanuts.common.enums.Contract;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class LadderOrderRef {
    private final long orderId;
    private final long userId;
    private final Contract contract;
}
