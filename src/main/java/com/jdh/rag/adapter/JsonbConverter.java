package com.jdh.rag.adapter;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.postgresql.util.PGobject;
import tools.jackson.databind.ObjectMapper;

import java.util.Map;

/**
 * Map&lt;String, Object&gt; ↔ PostgreSQL JSONB 변환기.
 * JSONB 컬럼에 String 을 setString() 으로 직접 넣으면 타입 오류가 발생하므로
 * PGobject(type="jsonb") 로 래핑하여 JDBC 드라이버에 전달한다.
 */
@Converter
public class JsonbConverter implements AttributeConverter<Map<String, Object>, Object> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public Object convertToDatabaseColumn(Map<String, Object> map) {
        try {
            PGobject pgObject = new PGobject();
            pgObject.setType("jsonb");
            pgObject.setValue(map != null ? MAPPER.writeValueAsString(map) : "{}");
            return pgObject;
        } catch (Exception e) {
            throw new IllegalStateException("JSONB 직렬화 실패", e);
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> convertToEntityAttribute(Object dbData) {
        if (dbData == null) return Map.of();
        try {
            String json = (dbData instanceof PGobject pg) ? pg.getValue() : dbData.toString();
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            throw new IllegalStateException("JSONB 역직렬화 실패", e);
        }
    }
}
