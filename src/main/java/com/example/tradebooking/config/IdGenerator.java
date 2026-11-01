package com.example.tradebooking.config;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.TimeBasedEpochGenerator;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Generates RFC 9562 UUIDv7 identifiers: 48-bit Unix-epoch millisecond timestamp followed by
 * random bits. They are globally unique without coordination, roughly time-ordered (so B-tree
 * primary-key inserts stay append-mostly), and their creation time can be read back from the id.
 */
@Component
public class IdGenerator {

    private final TimeBasedEpochGenerator generator = Generators.timeBasedEpochGenerator();

    public UUID newId() {
        return generator.generate();
    }
}
