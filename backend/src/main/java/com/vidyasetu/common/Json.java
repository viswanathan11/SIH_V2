package com.vidyasetu.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.util.List;
import java.util.Map;

/** Small static JSON helper (sorted keys for canonical hashing). */
public final class Json {
    public static final ObjectMapper MAPPER = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private Json() {}

    public static String write(Object o) {
        if (o == null) return null;
        try { return MAPPER.writeValueAsString(o); } catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static Map<String, Object> readMap(String s) {
        if (s == null || s.isBlank()) return new java.util.LinkedHashMap<>();
        try { return MAPPER.readValue(s, new TypeReference<java.util.LinkedHashMap<String, Object>>() {}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static Map<String, String> readStringMap(String s) {
        if (s == null || s.isBlank()) return new java.util.LinkedHashMap<>();
        try { return MAPPER.readValue(s, new TypeReference<java.util.LinkedHashMap<String, String>>() {}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static List<Object> readList(String s) {
        if (s == null || s.isBlank()) return new java.util.ArrayList<>();
        try { return MAPPER.readValue(s, new TypeReference<List<Object>>() {}); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }

    public static Object read(String s) {
        if (s == null || s.isBlank()) return null;
        try { return MAPPER.readValue(s, Object.class); } catch (Exception e) { return s; }
    }
}
