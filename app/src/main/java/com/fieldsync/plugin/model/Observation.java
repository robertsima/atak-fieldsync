package com.fieldsync.plugin.model;

public class Observation {
    public String id;
    public String operatorUID;
    public String callsign;
    public String text;
    public String category;
    public double latitude;
    public double longitude;
    public long timestamp;
    public boolean archived;
    public boolean important;   // operator-flagged as high priority
}
