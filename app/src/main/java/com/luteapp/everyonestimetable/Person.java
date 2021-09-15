package com.luteapp.everyonestimetable;

import java.util.ArrayList;

/**
 * This is used in a lot of places for different purposes. So don't assume all the
 * fields are set, they may not be.
 */
public class Person implements Comparable<Person>
{
    public int localId;
    public int serverId;
    public int latestTimetableId;
    public String fullName;
    public int type;
    boolean isVerified;
    public ArrayList<ArrayList<Period>> timetable;
    String latestTimetableUpdate;
    
    public Person(int localId, String fullName)
    {
        this.localId = localId;
        this.fullName = fullName;
    }
    
    public Person(int localId, int serverId, int latestTimetableId, String fullName, 
                  int type, boolean isVerified, ArrayList<ArrayList<Period>> timetable,
                  String latestTimetableUpdate)
    {
        this.localId = localId;
        this.serverId = serverId;
        this.latestTimetableId = latestTimetableId;
        this.fullName = fullName;
        this.type = type;
        this.timetable = timetable;
        this.isVerified = isVerified;
        this.latestTimetableUpdate = latestTimetableUpdate;
    }
    
    /**
     * So I can sort an ArrayList of people.
     */
    @Override
    public int compareTo(Person other)
    {
        return this.fullName.compareTo(other.fullName);
    }
}
