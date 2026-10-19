package com.pg_management.resident.storage;

import org.springframework.http.MediaType;

public record ResidentDocument(byte[] content, MediaType mediaType) {
}
