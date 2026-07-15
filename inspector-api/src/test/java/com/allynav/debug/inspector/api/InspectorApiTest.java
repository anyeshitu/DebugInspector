package com.allynav.debug.inspector.api;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class InspectorApiTest {
    @Test
    public void defaultsMatchProductPolicy() {
        RetentionPolicy policy = RetentionPolicy.builder().build();
        assertEquals(24L * 60L * 60L * 1000L, policy.getRetentionMillis());
        assertEquals(100L * 1024L * 1024L, policy.getMaxStoreBytes());
        assertEquals(250 * 1024, policy.getMaxHttpBodyBytes());
        assertEquals(64 * 1024, policy.getMaxEventPayloadBytes());
        assertEquals(10_000, policy.getMaxDatabaseExportRows());
        assertEquals(UiLanguage.SIMPLIFIED_CHINESE, InspectorConfig.builder().build().getUiLanguage());
    }

    @Test
    public void configNormalizesHeaderNamesAndKeepsBodyUnchanged() {
        InspectorConfig config = InspectorConfig.builder().redactHeaders("Authorization", "AUTH-TOKEN").build();
        assertTrue(config.getRedactedHeaderNames().contains("authorization"));
        assertTrue(config.getRedactedHeaderNames().contains("auth-token"));
        assertFalse(config.getRedactedHeaderNames().contains("password"));
    }

    @Test
    public void bodyAndSerialPayloadUseDefensiveCopies() {
        byte[] source = new byte[]{1, 2, 3};
        BodyData body = BodyData.builder().bytes(source).build();
        SerialEvent serial = SerialEvent.builder("port", SerialEvent.Direction.TX).payload(source).build();
        source[0] = 9;
        assertArrayEquals(new byte[]{1, 2, 3}, body.getBytes());
        assertArrayEquals(new byte[]{1, 2, 3}, serial.getPayload());
        byte[] returned = body.getBytes();
        returned[1] = 8;
        assertArrayEquals(new byte[]{1, 2, 3}, body.getBytes());
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidRetention() {
        RetentionPolicy.builder().maxStoreBytes(0).build();
    }
}
