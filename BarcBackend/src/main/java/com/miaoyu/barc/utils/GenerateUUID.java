package com.miaoyu.barc.utils;

import java.util.UUID;

public class GenerateUUID {
    private UUID uuid;

    public GenerateUUID() {
        setUuid();
    }
    public GenerateUUID(String [] args) {
        setUuid(args);
    }

    public void setUuid() {
        this.uuid = UUID.nameUUIDFromBytes(("" + System.currentTimeMillis()).getBytes());
    }

    public void setUuid(String[] args) {
        long timestamp = System.currentTimeMillis();
        if (args.length == 0){
            this.uuid = UUID.nameUUIDFromBytes(("" + timestamp).getBytes());
        } else {
            StringBuilder values = new StringBuilder();
            for (String arg : args) {
                values.append(arg);
            }
            this.uuid = UUID.nameUUIDFromBytes((values.toString() + timestamp).getBytes());
        }
    }

    public String getUuid36l() {
        return this.uuid.toString();
    }
    public String getUuid36u() {
        return this.uuid.toString().toUpperCase();
    }
    public String getUuid32l() {
        return this.uuid.toString().replace("-", "");
    }
    public String getUuid32u() {
        return this.uuid.toString().replace("-", "").toUpperCase();
    }
}
