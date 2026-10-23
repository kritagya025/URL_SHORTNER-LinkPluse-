package com.urlshortener.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class QrCodeServiceTest {

    private final QrCodeService qrCodeService = new QrCodeService();

    @Test
    @DisplayName("Generate QR code returns valid PNG byte array")
    void generateQrCode_Success() {
        byte[] qrBytes = qrCodeService.generateQrCode("https://example.com/xyz123", 256, 256);

        assertNotNull(qrBytes);
        assertTrue(qrBytes.length > 0);
        // PNG magic number: 0x89 0x50 0x4E 0x47 (byte: -119, 80, 78, 71)
        assertEquals((byte) 0x89, qrBytes[0]);
        assertEquals((byte) 0x50, qrBytes[1]);
        assertEquals((byte) 0x4E, qrBytes[2]);
        assertEquals((byte) 0x47, qrBytes[3]);
    }
}
