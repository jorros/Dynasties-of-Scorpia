package de.dynastiesofscorpia.models;

import org.bson.types.ObjectId;

public interface MapNode {
    public Player getPlayer();
    public ObjectId getId();
    public double getX();
    public double getY();
}