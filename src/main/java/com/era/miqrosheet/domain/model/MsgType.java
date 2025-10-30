package com.era.miqrosheet.domain.model;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum MsgType {
    UPDATE(2),
    MV(3),
    MULTI_UPDATE(4);
    private final Integer type;
}
