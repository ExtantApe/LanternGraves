package com.extantape.lanterngraves.grave;

import java.util.Arrays;

/**
 * One stack in a grave, kept as Paper's own item bytes so nothing about it is lost. The type and
 * amount are copies for people reading the file and for messages; the bytes are what pays out.
 */
public record StoredItem(String type, int amount, byte[] data) {
    public StoredItem {
        if (type == null || type.isBlank()) throw new IllegalArgumentException("type");
        if (amount < 1) throw new IllegalArgumentException("amount");
        if (data == null || data.length == 0) throw new IllegalArgumentException("data");
        data = data.clone();
    }

    @Override
    public byte[] data() {
        return data.clone();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StoredItem s && s.type.equals(type) && s.amount == amount && Arrays.equals(s.data, data);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * type.hashCode() + amount) + Arrays.hashCode(data);
    }
}
