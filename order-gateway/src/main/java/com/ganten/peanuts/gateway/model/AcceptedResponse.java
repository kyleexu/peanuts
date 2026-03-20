package com.ganten.peanuts.gateway.model;

import javax.validation.constraints.NotNull;

import com.ganten.peanuts.common.enums.Source;

import lombok.Data;

@Data
public class AcceptedResponse {
    @NotNull
    private String trackingId;
    @NotNull
    private String message;
    @NotNull
    private Source source;
    @NotNull
    private long acceptedAt;
}
