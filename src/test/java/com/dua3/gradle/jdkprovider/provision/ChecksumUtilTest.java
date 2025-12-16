package com.dua3.gradle.jdkprovider.provision;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ChecksumUtilTest {
    @Test
    void sha256AndVerify() throws IOException {
        Path tmp = Files.createTempFile("checksum-", ".txt");
        Files.writeString(tmp, "hello-javafx\n");

        String sum = ChecksumUtil.sha256Hex(tmp);
        assertNotNull(sum);
        assertEquals(64, sum.length());

        // should not throw
        ChecksumUtil.verifySha256(tmp, sum);

        // mismatch should throw
        assertThrows(IOException.class, () -> ChecksumUtil.verifySha256(tmp, "deadbeef"));
    }
}
