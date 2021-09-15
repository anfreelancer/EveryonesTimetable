package com.luteapp.everyonestimetable;

import java.util.StringTokenizer;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Information about a school. All came from the server and stored in prefs, no sqlite.
 */
public class School
{
    int serverId;
    String name;
    int[] timePeriods;
    String[] emailServers;
    
    /**
     * School is built from json originally when it comes from the server but also
     * later when the app is started (registration already complete) the object is
     * restored from json saved in prefs.
     */
    public School(JSONObject json) throws JSONException
    {
        serverId = json.getInt("schoolId");
        name = json.getString("name");
        
        StringTokenizer periodsTokens = new StringTokenizer(json.getString("timePeriods"), "-");
        timePeriods = new int[periodsTokens.countTokens()];
        for ( int i = 0; periodsTokens.countTokens() > 0; i++)
            timePeriods[i] = Integer.parseInt(periodsTokens.nextToken());
        
        StringTokenizer emailServersTokens = new StringTokenizer(json.getString("emailServers"), "|");
        emailServers = new String[emailServersTokens.countTokens()];
        for ( int i = 0; emailServersTokens.countTokens() > 0; i++)
            emailServers[i] = emailServersTokens.nextToken();
    }
    
    /**
     * I want to save all this info in the prefs and this seems to be the easiest way to do it.
     */
    public JSONObject  makeJson() throws JSONException
    {
        JSONObject object = new JSONObject();
        object.put("schoolId", serverId);
        object.put("name",  name);
        
        String timePeriodsString = "";
        for (int i = 0; i < timePeriods.length; i++)
        {
            if (i > 0)
                timePeriodsString += "-";
            timePeriodsString += Integer.toString(timePeriods[i]);
        }
        object.put("timePeriods", timePeriodsString);
        
        String emailServersString = "";
        for (int i = 0; i < emailServers.length; i++)
        {
            if (i > 0)
                emailServersString += "|";
            emailServersString += emailServers[i];
        }
        object.put("emailServers", emailServersString);
        
        return object;
    }
    
    /**
     * I think this is used to display the object in the dropdown view.
     */
    public String toString()
    {
        return name;
    }
}
