package com.muletracer;

import com.muletracer.io.Json;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppTest {

    @Test
    void formatsRupeesInIndianGrouping() {
        assertEquals("Rs 999", App.rupees(99_900));
        assertEquals("Rs 1,000", App.rupees(100_000));
        assertEquals("Rs 18,50,000", App.rupees(185_000_000));
        assertEquals("Rs 1,23,45,678", App.rupees(1_234_567_800));
    }

    @Test
    void jsonEscapesAndNests() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", "a\"b\n");
        m.put("n", 5);
        m.put("r", 0.5);
        m.put("list", List.of(1, true));
        m.put("none", null);
        assertEquals("{\"name\":\"a\\\"b\\n\",\"n\":5,\"r\":0.5,\"list\":[1,true],\"none\":null}", Json.write(m));
    }
}
