package com.miaoyu.barc.utils;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import java.io.IOException;

/** 审核命令只接受 JSON 布尔值，拒绝字符串/数字被静默转换为拒绝操作。 */
public class StrictBooleanDeserializer extends JsonDeserializer<Boolean> {
    @Override public Boolean deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (parser.hasToken(JsonToken.VALUE_TRUE)) return true;
        if (parser.hasToken(JsonToken.VALUE_FALSE)) return false;
        return (Boolean) context.handleUnexpectedToken(Boolean.class, parser);
    }
}
