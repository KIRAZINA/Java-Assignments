package com.duplicatefinder.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;

import static org.assertj.core.api.Assertions.*;

@DisplayName("FileInfo Tests")
class FileInfoTest {

    @Test
    @DisplayName("Should create FileInfo with all required fields")
    void testFileInfoCreation() {
        // Given
        String name = "test.txt";
        String path = "/home/user/test.txt";
        long size = 1024;
        Instant now = Instant.now();

        // When
        FileInfo fileInfo = new FileInfo(name, path, size, now);

        // Then
        assertThat(fileInfo.getName()).isEqualTo(name);
        assertThat(fileInfo.getPath()).isEqualTo(path);
        assertThat(fileInfo.getSize()).isEqualTo(size);
        assertThat(fileInfo.getLastModified()).isEqualTo(now);
    }

    @Test
    @DisplayName("Should have crc32 as null initially")
    void testCrc32IsNullInitially() {
        // Given
        FileInfo fileInfo = new FileInfo("test.txt", "/home/user/test.txt", 1024, Instant.now());

        // When
        Long crc32 = fileInfo.getCrc32();

        // Then
        assertThat(crc32).isNull();
        assertThat(fileInfo.isCrc32Computed()).isFalse();
    }

    @Test
    @DisplayName("Should set and get crc32 value")
    void testSetCrc32() {
        // Given
        FileInfo fileInfo = new FileInfo("test.txt", "/home/user/test.txt", 1024, Instant.now());
        long crc32Value = 12345L;

        // When
        fileInfo.setCrc32(crc32Value);

        // Then
        assertThat(fileInfo.getCrc32()).isEqualTo(crc32Value);
        assertThat(fileInfo.isCrc32Computed()).isTrue();
    }

    @Test
    @DisplayName("Two FileInfo objects with same path should be equal")
    void testEqualityByPath() {
        // Given
        String name = "test.txt";
        String path = "/home/user/test.txt";
        long size = 1024;
        Instant now = Instant.now();

        FileInfo fileInfo1 = new FileInfo(name, path, size, now);
        FileInfo fileInfo2 = new FileInfo(name, path, size, now);

        // When & Then
        assertThat(fileInfo1).isEqualTo(fileInfo2);
    }

    @Test
    @DisplayName("Two FileInfo objects with different paths should not be equal")
    void testInequalityByPath() {
        // Given
        FileInfo fileInfo1 = new FileInfo("test.txt", "/home/user/test1.txt", 1024, Instant.now());
        FileInfo fileInfo2 = new FileInfo("test.txt", "/home/user/test2.txt", 1024, Instant.now());

        // When & Then
        assertThat(fileInfo1).isNotEqualTo(fileInfo2);
    }

    @Test
    @DisplayName("Should have same hashCode for FileInfo with same path")
    void testHashCodeByPath() {
        // Given
        String path = "/home/user/test.txt";
        FileInfo fileInfo1 = new FileInfo("test.txt", path, 1024, Instant.now());
        FileInfo fileInfo2 = new FileInfo("test.txt", path, 2048, Instant.now());

        // When & Then
        assertThat(fileInfo1.hashCode()).isEqualTo(fileInfo2.hashCode());
    }

    @Test
    @DisplayName("Should return meaningful toString")
    void testToString() {
        // Given
        FileInfo fileInfo = new FileInfo("test.txt", "/home/user/test.txt", 1024, Instant.now());
        fileInfo.setCrc32(999L);

        // When
        String result = fileInfo.toString();

        // Then
        assertThat(result)
            .contains("test.txt")
            .contains("/home/user/test.txt")
            .contains("1024")
            .contains("999");
    }

    @Test
    @DisplayName("Should handle zero-sized files")
    void testZeroSizedFile() {
        // Given
        FileInfo fileInfo = new FileInfo("empty.txt", "/home/user/empty.txt", 0, Instant.now());

        // When & Then
        assertThat(fileInfo.getSize()).isZero();
    }

    @Test
    @DisplayName("Should handle large file sizes")
    void testLargeFileSize() {
        // Given
        long largeSize = 1024L * 1024 * 1024 * 10; // 10 GB
        FileInfo fileInfo = new FileInfo("large.iso", "/home/user/large.iso", largeSize, Instant.now());

        // When & Then
        assertThat(fileInfo.getSize()).isEqualTo(largeSize);
    }
}
