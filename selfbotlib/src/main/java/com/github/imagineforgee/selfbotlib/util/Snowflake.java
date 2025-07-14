package com.github.imagineforgee.selfbotlib.util;

import java.time.Instant;

public final class Snowflake {
    private final long id;

    private static final long DISCORD_EPOCH = 1420070400000L; // Jan 1, 2015 UTC

    public Snowflake(long id) {
        this.id = id;
    }

    public static Snowflake of(long id) {
        return new Snowflake(id);
    }

    public static Snowflake of(String idStr) {
        return new Snowflake(Long.parseUnsignedLong(idStr));
    }

    public long getId() {
        return id;
    }

    public String asString() {
        return Long.toUnsignedString(id);
    }

    public long getTimestamp() {
        return (id >> 22) + DISCORD_EPOCH;
    }

    public Instant getInstant() {
        return Instant.ofEpochMilli(getTimestamp());
    }

    @Override
    public String toString() {
        return asString();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Snowflake)) return false;
        return ((Snowflake) obj).id == this.id;
    }

    @Override
    public int hashCode() {
        return Long.hashCode(id);
    }

    public int compareTo(Snowflake other) {
        return Long.compareUnsigned(this.id, other.id);
    }

}

