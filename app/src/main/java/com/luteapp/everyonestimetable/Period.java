package com.luteapp.everyonestimetable;

import java.io.Serializable;
import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This stores information about one time period, with a specific start and end time.
 * The period may be full (has at lease a courseCode or a room) 
 * or empty, in which case the only relevant info stored here is the time range.
 */
public class Period implements Serializable
{
    // Auto-generated by Eclipse for me to make it serializable
    private static final long serialVersionUID = 2789954099087907752L;
    static final String TAG = "EveryonesTimetable";
    String courseCode;
    int periodNum;
    String room;
    
    /**
     * Obviously empty period by default.
     */
    public Period(int periodNumIn)
    {
        periodNum = periodNumIn;
        
        // For some reason if I don't do this I end up with nullPointerExceptions
        // when comparing these with ""
        courseCode = "";
        room = "";
    }
    
    /**
     * Compare the time range of this period with another.
     */
    public boolean isSameTimeAs(Period other)
    {
        if (periodNum == other.periodNum)
            return true;
        else
            return false;
    }
    
    /**
     * Check whether there's something in this period or it's an empty stub.
     */
    public boolean isFull()
    {
        if (!courseCode.equalsIgnoreCase("") || !room.equalsIgnoreCase(""))
            return true;
        else
            return false;
    }
    
    /**
     * Need this because it takes so much work to initialize this array of arrays.
     */
    public static ArrayList<ArrayList<Period>> makeEmptyTimetable()
    {
        // Allocate array of days
        ArrayList<ArrayList<Period>> emptyTimetable = new ArrayList<ArrayList<Period>>();
        
        for (int dayNum = 0; dayNum < 5; dayNum++)
        {
            // Allocate each day
            ArrayList<Period> day;
            day = new ArrayList<Period>();
            emptyTimetable.add(day);
            
            // Allocate each period
            for (int periodNum = 0; periodNum < MainActivity.mySchool.timePeriods.length / 4; periodNum++)
            {
                Period period = new Period(periodNum);
                day.add(period);
            }
        }
        return emptyTimetable;
    }
    
    /**
     * Self-explanatory name. The format expected is:
     * [{"periodNum":"2","courseCode":"DPS924","room":"S2154","day":"0"},...]
     */
    public static ArrayList<ArrayList<Period>> makeTimetableFromJson(JSONArray jsonPeriods)
        throws JSONException
    {
        ArrayList<ArrayList<Period>> timetable = Period.makeEmptyTimetable();
        
        // Iterate all the periods given to me in the JSON, parse them out into 
        // Java variables, and add them to the timetable array of arrays.
        for (int i = 0; i < jsonPeriods.length(); i++)
        {
            JSONObject jsonPeriod;
            jsonPeriod = jsonPeriods.getJSONObject(i);
            String courseCode = jsonPeriod.getString("courseCode");
            int periodNum = jsonPeriod.getInt("periodNum");
            int day = jsonPeriod.getInt("day");
            String room = jsonPeriod.getString("room");

            Period period = timetable.get(day).get(periodNum);
            period.courseCode = courseCode;
            period.room = room;
        }
        
        return timetable;
    }
    
    /**
     * Self-explanatory name. The format created is:
     * [{"periodNum":"2","courseCode":"DPS924","room":"S2154","day":"0"},...]
     */
    public static JSONArray makeJsonFromTimetable(ArrayList<ArrayList<Period>> timetable)
    {
        JSONArray jsonPeriods = new JSONArray(); 
        
        // For each day
        for (int dayNum = 0; dayNum < 5; dayNum++) 
        {
            ArrayList<Period> todayPeriods = timetable.get(dayNum);
            
            // For each period
            for (int periodNum = 0; periodNum < MainActivity.mySchool.timePeriods.length / 4; periodNum++) 
            {
                Period period = todayPeriods.get(periodNum);
                
                if (period.isFull())
                {
                    JSONObject jsonPeriod = new JSONObject();
                    jsonPeriods.put(jsonPeriod);
                    try 
                    {
                        jsonPeriod.put("courseCode", period.courseCode);
                        jsonPeriod.put("periodNum", period.periodNum);
                        jsonPeriod.put("day", dayNum);
                        jsonPeriod.put("room", period.room);
                    } 
                    catch (JSONException e) 
                    {
                        e.printStackTrace();
                    }
                }
            }
        }
        
        return jsonPeriods;
    }
}
