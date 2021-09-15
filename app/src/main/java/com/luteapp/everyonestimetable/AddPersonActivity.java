package com.luteapp.everyonestimetable;

import java.net.URLEncoder;
import java.util.ArrayList;
import org.json.JSONException;
import org.json.JSONObject;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.TextView;
import android.widget.Toast;

//Toast.makeText(this, "asd", Toast.LENGTH_SHORT).show();

/**
 * This is only for adding brand new people to both the server and the app. Adding
 * existing users is done in FindTimetableActivity.
 */
public class AddPersonActivity extends Activity 
{
    static final String TAG = "EveryonesTimetable";
    SharedPreferences preferences;
    String myEmailAddress;
    String myPasswordHash;
    SQLiteAdapter sqliteAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_person);
        
        preferences = getSharedPreferences("appPreferences", MODE_PRIVATE);
        myEmailAddress = preferences.getString("myEmailAddress", "shouldnt@get.this");
        myPasswordHash = preferences.getString("myPasswordHash", "shouldntgetthis");
        
        sqliteAdapter = new SQLiteAdapter(this);
    }

    /**
     * The callback for the add person button.
     */
    public void addPersonNowCbk(View v)
    {
        TextView textView;
        
        textView = (TextView)(findViewById(R.id.addPersonNameFld));
        String name = textView.getText().toString();
        String studentOrProf;
        RadioButton radioButton = (RadioButton)findViewById(R.id.addPersonStudentRdo);
        if (radioButton.isChecked())
            studentOrProf = "1";
        else
            studentOrProf = "2";
        
        if (name.isEmpty())
        {
            Globals.showNotAllowed(this, "Please enter a full name, or else you won't be able to tell " +
                                         "one timetable from another!");
        }
        else
        {
            // Disable/hide the buttons
            Button addBtn = (Button)findViewById(R.id.addPersonBtn);
            addBtn.setVisibility(View.GONE);
            
            // And make sure the keyboard dissapears too
            InputMethodManager imm = (InputMethodManager)getSystemService(
                                                            Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(textView.getWindowToken(), 0);
            
            // Show spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.addPersonSpinner);
            spinner.setVisibility(View.VISIBLE);
            
            // Start thread to register online
            new AddPersonTask().execute(name, studentOrProf);
        }
    }

    /**
     * Use the web service to add a new person.
     */
    private class AddPersonTask extends AsyncTask<String, Void, Boolean> 
    {
        String errorText = null;
        int svrPersonId;
        int svrTimetableId;
        String fullName;
        String typeString;
        int type;
        
        /**
         * credentials[0] is the full name
         * credentials[1] is the type (1 for student, 2 for prof)
         * 
         * Returns success or fail boolean, error stored in member variable.
         */
        @SuppressWarnings("deprecation")
        public Boolean doInBackground(String... credentials) 
        {
            fullName = credentials[0];
            typeString = credentials[1];
            type = Integer.parseInt(typeString);
            
            String command = "addUnregisteredUser" +
                    "&schoolId=" + Integer.toString(MainActivity.mySchool.serverId) +
                    "&fullName=" + URLEncoder.encode(fullName) +
                    "&type=" + URLEncoder.encode(typeString) +
                    "&requesterEmail=" + URLEncoder.encode(myEmailAddress) +
                    "&requesterPasswordHash=" + myPasswordHash;
            
            String result = null;
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONObject contents = json.getJSONObject("contents");
                    svrPersonId = contents.getInt("personId");
                    svrTimetableId = contents.getInt("latestTimetableId");
                    if (svrPersonId > 0 && svrTimetableId > 0)
                    {
                        // Registration successful on the server. Which also means
                        // there will already be an empty timetable created on the server.
                        return true;
                    }
                    else
                    {
                        throw new ETException("An error happened on the server.", null);
                    }
                }
                else
                    errorText = Globals.formatServerErrorStack(json);
            }
            catch (JSONException e) 
            { 
                errorText = "Got bad result from server, perhaps it is offline?";
                e.printStackTrace();
            }
            catch (ETException e) 
            {
                errorText = e.getMessage();
                e.printStackTrace();
            }
            
            return false;
        }
        
        /**
         * Now either show an error and go back to the previous screen 
         * or continue the registration process.
         */
        public void onPostExecute(Boolean okSoFar) 
        {
            if (errorText != null)
            {
                Toast.makeText(AddPersonActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                // Reenable/show the buttons
                Button addBtn = (Button)findViewById(R.id.addPersonBtn);
                addBtn.setVisibility(View.VISIBLE);
                
                // Hide spinner
                ProgressBar spinner = (ProgressBar)findViewById(R.id.addPersonSpinner);
                spinner.setVisibility(View.GONE);
                
                return;
            }
            
            finishAddingPerson(svrPersonId, svrTimetableId, fullName, type);
        }
    }
    
    /**
     * By this point the person and empty timetable have been successfully created on the server
     * so all I have left to do here is add that person (and empty timetable) to sqlite.
     */
    void finishAddingPerson(int svrPersonId, int svrTimetableId, String fullName, int type)
    {

        ArrayList<ArrayList<Period>> timetable;
        timetable = Period.makeEmptyTimetable();
        
        // Add the timetable to the list
        Log.d(TAG, "Trying to add timetable to list");
        sqliteAdapter.open();
        int sqlitePersonId = sqliteAdapter.addPerson(svrPersonId, svrTimetableId, 
                                                     fullName, type);
        sqliteAdapter.saveTimetable(sqlitePersonId, svrTimetableId, timetable);
        sqliteAdapter.close();
        
        // Exit this activity, returning to the main screen
        finish();
    }
}
