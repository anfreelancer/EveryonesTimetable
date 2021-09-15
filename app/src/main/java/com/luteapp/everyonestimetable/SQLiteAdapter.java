package com.luteapp.everyonestimetable;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * This has functions to help me interact with the sqlite database which
 * I use to store all the timetables locally.
 */
public class SQLiteAdapter 
{
    static final String TAG = "EveryonesTimetable";
    static final String DB_FILE_NAME = "localCache.db";
    static final int DB_VERSION = 1;
    final Context context;
    DatabaseOpenHelper dbOpenHelper;
    SQLiteDatabase db;
    int numTimesOpenCalled = 0;
    
    /**
     * This will make sure that when the app is first started - it has a working
     * database. Could have used the SQLiteOpenHelper.onCreate() down below
     * but this is easier.
     */
    @SuppressLint("SdCardPath")
    public SQLiteAdapter(Context ctx)
    {
        context = ctx;
        
        try
        {
            String destPath = "/data/data/" + context.getPackageName() + "/databases/";
            
            File destPathFile =  new File(destPath);
            if (!destPathFile.exists())
                destPathFile.mkdirs();
            
            File destFile = new File(destPath + DB_FILE_NAME);
            if (!destFile.exists())
            {
                Log.d(TAG, "First run, copying default database");
                copyFile(context.getAssets().open(DB_FILE_NAME),
                         new FileOutputStream(destPath + "/" + DB_FILE_NAME));
            }
        } 
        catch (FileNotFoundException e) { e.printStackTrace(); } 
        catch (IOException e) { e.printStackTrace(); }
        
        dbOpenHelper = new DatabaseOpenHelper(context);
    }
    
    /**
     * This function comes from the database example in the book. I've no idea 
     * why it's necessary, does Android really not have a function that does this?
     */
    public void copyFile(InputStream inputStream, OutputStream outputStream) throws IOException
    {
        byte[] buffer = new byte[1024];
        int length;
        while ((length = inputStream.read(buffer)) > 0) 
            outputStream.write(buffer, 0, length);
        inputStream.close();
        outputStream.close();
    }
    
    /**
     * User: call this before using any of the other functions from here.
     */
    public void open()
    {
        synchronized(this)
        {
            numTimesOpenCalled++;
        }
        try
        {
            db = dbOpenHelper.getWritableDatabase();
        }
        catch (SQLiteException e) { e.printStackTrace(); }
    }
    
    /**
     * User: call this after you're done using the other functions from here.
     */
    public void close() 
    {
        synchronized(this)
        {
            numTimesOpenCalled--;
            if (numTimesOpenCalled <= 0)
                dbOpenHelper.close();
        }
    }
    
    /**
     * Annoying class I apparently have to create so that I can get a handle to the database. 
     */
    private static class DatabaseOpenHelper extends SQLiteOpenHelper
    {
        DatabaseOpenHelper(Context context)
        {
            super(context, DB_FILE_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db)
        {
            // This needs to be implemented but it doesn't appear to be necessary to do
            // anything inside.
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion)
        {
            // This needs to be implemented but it doesn't appear to be necessary to do
            // anything inside.
        }
    }

    /**
     * Create a new person and return their local (sqlite) id.
     * All parameters are required.
     */
    public int addPerson(int svrPersonId, int svrTimetableId,
                         String fullName, int type)
    {
        // Create row in Person
        ContentValues values = new ContentValues();
        values.put("svrPersonId", svrPersonId);
        values.put("svrTimetableId", svrTimetableId);
        values.put("fullname", fullName);
        values.put("type", type);
        long personId = db.insert("Person", null, values);
        
        return (int)personId;
    }
    
    public void deleteEverything()
    {
        db.delete("TimePeriod", null, null);
        db.delete("Person", null, null);
    }
    
    /**
     * Delete the person and their associated timetable
     */
    public void deletePerson(int personId)
    {
        db.delete("TimePeriod", "personId=" + personId, null);
        db.delete("Person", "_id=" + personId, null);
    }
    
    /**
     * Check if already have this person in sqlite.
     */
    public boolean havePersonInDb(int svrPersonId)
    {
        Cursor cursor;
        cursor = db.query("Person", 
                          new String[] {"svrPersonId"}, 
                          "svrPersonId=" + svrPersonId, 
                          null, null, null, null, null);
        Log.d(TAG, "cursor count for " + svrPersonId + " is " + cursor.getCount());
        if (cursor.getCount() == 0)
            return false;
        else
            return true;
    }
    
    /**
     * What is the name of the person with this userId?
     */
    public String getPersonNameById(int personId)
    {
        Cursor cursor;
        cursor = db.query("Person", 
                          new String[] {"fullName"}, 
                          "_id=" + personId, 
                          null, null, null, null, null);
        if (cursor.getCount() == 0)
            return "Error getting person name";
        else
        {
            cursor.moveToFirst();
            return cursor.getString(0);
        }
    }

    /**
     * What is the name of the person with this serverId?
     */
    public String getPersonNameByServerId(int serverPersonId)
    {
        Cursor cursor;
        cursor = db.query("Person", 
                          new String[] {"fullName"}, 
                          "svrPersonId=" + serverPersonId, 
                          null, null, null, null, null);
        if (cursor.getCount() == 0)
            return "Error getting person name";
        else
        {
            cursor.moveToFirst();
            return cursor.getString(0);
        }
    }
    
    /**
     * Put the entire timetable for the user into an array of arrays (days full of periods).
     * The outer array is expected to have five elements already.
     */
    public boolean getPersonTimetable(int personId, ArrayList<ArrayList<Period>> timetable)
    {
        Log.d(TAG, "getPersonTimetable(" + Integer.toString(personId) + ")");
        
        Cursor cursor;
        
        // Get every time period for this timetable
        cursor = db.query("TimePeriod",
                          new String[] {"courseCode", "periodNum", "dayNum", "room"}, // SELECT
                          "personId=" + personId, // WHERE
                          null, null, null,
                          "periodNum", // ORDER BY 
                          null); 
        if (cursor.getCount() == 0)
        {
            return true;
        }
        
        // And parse it all into todayPeriods
        cursor.moveToFirst();
        do
        {
            int periodNum = cursor.getInt(1);
            int dayNum = cursor.getInt(2);
            
            Period period = timetable.get(dayNum).get(periodNum);
            period.courseCode = cursor.getString(0);
            period.room = cursor.getString(3);
            
        } while (cursor.moveToNext());
        /* END GET TIMETABLE */
        
        return true;
    }
    
    /**
     * Sometimes I need to know what the ID in sqlite is for a person if I
     * only know their server ID.
     */
    public int getPersonId(int personServerId)
    {
        Cursor cursor;
        cursor = db.query("Person", 
                          new String[] {"_id"}, 
                          "svrPersonId=" + personServerId, 
                          null, null, null, null, null);
        if (cursor.getCount() == 0)
            return -1;
        else
        {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
    
    /**
     * Sometimes I need to know what the ID on the server is for a person if I
     * only know their sqlite ID.
     */
    public int getPersonSvrId(int personLiteId)
    {
        Cursor cursor;
        cursor = db.query("Person", 
                          new String[] {"svrPersonId"}, 
                          "_id=" + personLiteId, 
                          null, null, null, null, null);
        if (cursor.getCount() == 0)
            return -1;
        else
        {
            cursor.moveToFirst();
            return cursor.getInt(0);
        }
    }
    
    /**
     * Look through the entire local database for course codes and make a list.
     * Maybe it would help if I sorted this by popularity but am not really sure.
     */
    public ArrayList<String> getListOfCourseCodes()
    {
        ArrayList<String> courseCodes = new ArrayList<String>();
        
        Cursor cursor;
        
        cursor = db.query("TimePeriod", new String[] {"courseCode"}, null, null, null, null, null, null); 
        if (cursor.getCount() < 1)
            return courseCodes;
        
        cursor.moveToFirst();
        do
        {
            String courseCode = cursor.getString(0);
            if (!courseCodes.contains(courseCode))
                courseCodes.add(courseCode);
            
        } while (cursor.moveToNext());
        
        // For debugging:
//        courseCodes.add("OPS235");
//        courseCodes.add("OPS335");
//        courseCodes.add("OPS435b");
//        courseCodes.add("OPS435a");
        
        return courseCodes;
    }

    /**
     * Look through the entire local database for room numbers and make a list.
     * Eventually this has to be integrated somehow with the server database but
     * for now it's doing the same thing as getListOfCourseCodes() does for course codes.
     */
    public ArrayList<String> getListOfRoomNumbers()
    {
        ArrayList<String> roomNumbers = new ArrayList<String>();
        
        Cursor cursor;
        
        cursor = db.query("TimePeriod", new String[] {"room"}, null, null, null, null, null, null); 
        if (cursor.getCount() < 1)
            return roomNumbers;
        
        cursor.moveToFirst();
        do
        {
            String roomNumber = cursor.getString(0);
            if (!roomNumbers.contains(roomNumber))
                roomNumbers.add(roomNumber);
            
        } while (cursor.moveToNext());
        
        return roomNumbers;
    }

    /**
     * Return the person's name and ID for all the ones stored in the database.
     * Except the one for the app owner (with ID ignoreThisOne), that's a special case.
     */
    public ArrayList<Person> getListOfTimetables(int ignoreThisOne)
    {
        ArrayList<Person> people = new ArrayList<Person>();
        
        Cursor cursor;
        
        // First find the Person IDs for everyone
        cursor = db.query("Person", 
                          new String[] {"_id", "svrPersonId", "svrTimetableId", "fullName", "type"}, 
                          null, null, null, null, null, null); 
        if (cursor.getCount() < 1)
        {
            return people;
        }
        
        // And find every one of those persons' Timetable IDs
        cursor.moveToFirst();
        do
        {
            int personId = cursor.getInt(0);
            int svrPersonId = cursor.getInt(1);
            int svrTimetableId = cursor.getInt(2);
            String personName = cursor.getString(3);
            int type = cursor.getInt(4);
            
            if (personId == ignoreThisOne)
                continue;
            
            people.add(new Person(personId, svrPersonId, svrTimetableId, personName, type, false, null, ""));
            
        } while (cursor.moveToNext());
        
        return people;
    }
    
    /**
     * Add the rows into TimePeriod for this timetable.
     * Assumes that the Person for that person already exists.
     */
    public int saveTimetable(int personId, int svrTimetableId, ArrayList<ArrayList<Period>> timetable)
    {
        // Delete all existing TimePeriod rows for this timetable.
        int numDeletedRows;
        numDeletedRows = db.delete("TimePeriod", "personId=" + personId, null);
        Log.d(TAG, "Deleted " + Integer.toString(numDeletedRows) + " rows before saving new timetable");
        
        // For each day
        for (int dayNum = 0; dayNum < 5; dayNum++)
        {
            ArrayList<Period> periodsForThisDay = timetable.get(dayNum);
            // For each period
            for (int periodNum = 0; periodNum < periodsForThisDay.size(); periodNum++)
            {
                Period period = periodsForThisDay.get(periodNum);
                
                // Add a TimePeriod row to the database
                // But only if there's something in this period
                if (period.isFull())
                {
                    ContentValues values = new ContentValues();
                    values.put("personId", personId);
                    values.put("courseCode", period.courseCode);
                    values.put("periodNum", period.periodNum);
                    values.put("dayNum", dayNum);
                    values.put("room", period.room);
                    
                    long rc = db.insert("TimePeriod", null, values);
                    Log.d(TAG, "Inserting row for " + period.courseCode + " returned " + Long.toString(rc));
                    if (rc == -1)
                        return 0;
                }
            }
        }
        
        // Update latest timetableId also
        ContentValues values = new ContentValues();
        values.put("svrTimetableId", svrTimetableId);
        long rc = db.update("Person", values, "_id=" + personId, null);
        Log.d(TAG, "Updating svrTimetableId to " + svrTimetableId + " returned " + rc);
        if (rc == -1)
            return 0;
        
        return 1;
    }
}
