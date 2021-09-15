package com.luteapp.everyonestimetable;

import java.net.URLEncoder;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.os.AsyncTask;
import android.os.Bundle;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

//Toast.makeText(this, "asd", Toast.LENGTH_SHORT).show();

/**
 * This activity is used to search for a timetable online and (if the user wants to)
 * download that timetable and store it in the local list.
 */
public class FindTimetableActivity extends Activity implements Globals.CheckIsUserVerifiedCallback
{
    static final String TAG = "EveryonesTimetable";
    FoundPersonAdapter foundTimetablesAdapter;
    SQLiteAdapter sqliteAdapter;
    SharedPreferences preferences;
    int myPersonLiteId;
    String myEmailAddress;
    String myPasswordHash;
    Boolean myEmailWasVerified;
    static int TIMETABLE_PREVIEW_REQUEST = 0; // Code to catch the preview activity returning
    Person personBeingPreviewed;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.find_timetable);

        preferences = getSharedPreferences("appPreferences", MODE_PRIVATE);
        // -1 also means registration skipped, but that should be good in this activity
        myPersonLiteId = preferences.getInt("myPersonLiteId", -1);
        myEmailAddress = preferences.getString("myEmailAddress", "shouldnt@get.this");
        myPasswordHash = preferences.getString("myPasswordHash", "shouldntgetthis");
        myEmailWasVerified = preferences.getBoolean("myEmailWasVerified", false);
        
        sqliteAdapter = new SQLiteAdapter(this);
        
        // This is the list for results
        ListView listView = (ListView)findViewById(R.id.findPersonLV);
        // This adapter is used to connect the list of results found with
        // a the listview above and also a custom view for each row.
        foundTimetablesAdapter = new FoundPersonAdapter(this, R.layout.find_timetable_row);
        listView.setAdapter(foundTimetablesAdapter);
    }
    
    /**
     * The callback for the search button.
     */
    public void searchCbk(View v)
    {
        EditText textBox = (EditText)findViewById(R.id.findPersonFld);
        String lookFor = textBox.getText().toString();
        
        // Make sure the keyboard dissapears, so can see toast will error if it happens 
        InputMethodManager imm = (InputMethodManager)getSystemService(
                                                        Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
        
        if (lookFor.length() > 0)
        {
            foundTimetablesAdapter.clear();
            
            // Show progress spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.findPersonSpinner);
            spinner.setVisibility(View.VISIBLE);
            View view = findViewById(R.id.findPersonSpacerView);
            view.setVisibility(View.VISIBLE);
            // Hide results list
            ListView listView = (ListView)findViewById(R.id.findPersonLV);
            listView.setVisibility(View.GONE);
            
            new TimetableFinder().execute(lookFor);
        }
    }
    
    /**
     * This only exists because I wanted to override getView() so that each row in the list
     * would show more than one string (see find_timetable_row.xml).
     * A similar class is in the register code in MainActivity.
     */
    private class FoundPersonAdapter extends ArrayAdapter<Person>
    {
        Context context;
        int layoutResourceId;
        
        public FoundPersonAdapter(Context context, int layoutResourceId)
        {
            super(context, layoutResourceId);
            
            this.context = context;
            this.layoutResourceId = layoutResourceId;
        }
        
        @Override
        public View getView(int position, View convertView, ViewGroup parent) 
        {
            View row = convertView;
            
            if(row == null)
            {
                LayoutInflater inflater = ((Activity)context).getLayoutInflater();
                row = inflater.inflate(layoutResourceId, parent, false);
                row.setOnClickListener(new View.OnClickListener()
                {
                    // When row is clicked - try to add the person to the list of timetables.
                    // No point in having this class outside of here, it can't be used for
                    // anything else.
                    
                    @Override
                    public void onClick(View row) 
                    {
                        int mySvrPersonId = -1;
                        if (myPersonLiteId != -1)
                        {
                            sqliteAdapter.open();
                            mySvrPersonId = sqliteAdapter.getPersonSvrId(myPersonLiteId);
                            sqliteAdapter.close();
                        }
                        
                        Person foundPerson = (Person)row.getTag();
                        
                        // Don't allow adding my timetable
                        if (foundPerson.serverId == mySvrPersonId)
                        {
                            Toast.makeText(FindTimetableActivity.this, "This is your timetable already!", Toast.LENGTH_LONG).show();
                            return;
                        }
                        
                        // And don't allow duplicate timetables either
                        sqliteAdapter.open();
                        boolean havePersonInDb = sqliteAdapter.havePersonInDb(foundPerson.serverId);
                        sqliteAdapter.close();
                        if (havePersonInDb)
                        {
                            Toast.makeText(FindTimetableActivity.this, "You already have this timetable in your list!", Toast.LENGTH_LONG).show();
                            return;
                        }
                        
                        // Add the timetable to the list
                        Log.d(TAG, "Trying to add timetable to list");
                        sqliteAdapter.open();
                        int sqlitePersonId = sqliteAdapter.addPerson(foundPerson.serverId, foundPerson.latestTimetableId, 
                                                                     foundPerson.fullName, foundPerson.type);
                        sqliteAdapter.saveTimetable(sqlitePersonId, foundPerson.latestTimetableId, foundPerson.timetable);
                        sqliteAdapter.close();
                        
                        // Exit this activity, returning to the main screen
                        finish();
                    }
                });
                ImageButton previewBtn = (ImageButton)row.findViewById(R.id.findTimetableRowPreviewBtn);
                previewBtn.setOnClickListener(new View.OnClickListener()
                {
                    // When preview button is clicked - start a show timetable activity
                    // with customized preview buttons.
                    // No point in having this class outside of here, it can't be used for
                    // anything else.
                    
                    @Override
                    public void onClick(View button) 
                    {
                        int mySvrPersonId = -1;
                        if (myPersonLiteId != -1)
                        {
                            sqliteAdapter.open();
                            mySvrPersonId = sqliteAdapter.getPersonSvrId(myPersonLiteId);
                            sqliteAdapter.close();
                        }
                        
                        View row = (View)button.getParent().getParent();
                        Person foundPerson = (Person)row.getTag();
                        
                        // Don't allow adding my timetable
                        if (foundPerson.serverId == mySvrPersonId)
                        {
                            Toast.makeText(FindTimetableActivity.this, "This is your timetable already!", Toast.LENGTH_LONG).show();
                            return;
                        }
                        
                        // And don't allow duplicate timetables either
                        sqliteAdapter.open();
                        boolean havePersonInDb = sqliteAdapter.havePersonInDb(foundPerson.serverId);
                        sqliteAdapter.close();
                        if (havePersonInDb)
                        {
                            Toast.makeText(FindTimetableActivity.this, "You already have this timetable in your list!", Toast.LENGTH_LONG).show();
                            return;
                        }
                        
                        // Save these for when the preview activity terminates
                        personBeingPreviewed = foundPerson;
                        
                        // Start the preview activity
                        Intent myIntent = new Intent(FindTimetableActivity.this, ShowTimetableActivity.class);
                        myIntent.putExtra("userId", -3);
                        myIntent.putExtra("personName", foundPerson.fullName);
                        myIntent.putExtra("previewTimetable", foundPerson.timetable);
                        startActivityForResult(myIntent, TIMETABLE_PREVIEW_REQUEST);
                    }
                });
            }
            
            TextView name = (TextView)row.findViewById(R.id.name);
            TextView description = (TextView)row.findViewById(R.id.description);
            
            Person rowData = getItem(position);
            
            row.setTag(rowData);
            name.setText(rowData.fullName);
            String descriptionText = Globals.personType[rowData.type];
            if (rowData.isVerified)
                descriptionText += ", verified";
            else
                descriptionText += ", unverified";
            descriptionText += "\nLast update: " + rowData.latestTimetableUpdate.split(" ")[0];
            description.setText(descriptionText);
            
            return row;
        }
    };
    
    /**
     * This gets called automatically when a child activity returns a result.
     * Right now I only have one - a preview timetable activity. 
     */
    protected void onActivityResult(int requestCode, int resultCode, Intent data) 
    {
        if (requestCode == TIMETABLE_PREVIEW_REQUEST) 
        {
            if (resultCode == ShowTimetableActivity.PREVIEW_RESULT_YES) 
            {
                // Add the timetable to the list
                Log.d(TAG, "Trying to add timetable to list");
                
                sqliteAdapter.open();
                int sqlitePersonId = sqliteAdapter.addPerson(personBeingPreviewed.serverId, 
                                                             personBeingPreviewed.latestTimetableId, 
                                                             personBeingPreviewed.fullName, 
                                                             personBeingPreviewed.type);
                sqliteAdapter.saveTimetable(sqlitePersonId, 
                                            personBeingPreviewed.latestTimetableId, 
                                            personBeingPreviewed.timetable);
                sqliteAdapter.close();
                
                // Exit this activity, returning to the main screen
                finish();
            }
        }
    }
    
    /**
     * The AsyncTask I use to look for name matches online.
     * The results (list of people, each with a timetable already downloaded) are 
     * passed back via foundTimetablesAdapter
     */
    private class TimetableFinder extends AsyncTask<String, Void, ArrayList<Person>> 
    {
        String errorText = null;
        
        @SuppressWarnings("deprecation")
        @Override
        protected ArrayList<Person> doInBackground(String... params) 
        {
            ArrayList<Person> resultList = new ArrayList<Person>();
            
            String command = "getSuggestionsForPersonName" + 
                             "&name=" + URLEncoder.encode(params[0]) +
                             "&schoolId=" + MainActivity.mySchool.serverId;
            
            String result;
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONArray peopleArray = json.getJSONArray("contents");
                    for (int i = 0; i < peopleArray.length(); i++)
                    {
                        JSONObject person = peopleArray.getJSONObject(i);
                        
                        int serverId = person.getInt("personId");
                        int latestTimetableId = person.getInt("latestTimetableId");
                        String fullName = person.getString("fullName");
                        int type = person.getInt("type");
                        boolean isVerified = person.getBoolean("emailVerified");
                        ArrayList<ArrayList<Period>> timetable = Globals.getTimetableForPerson(latestTimetableId);
                        String latestTimetableUpdate = person.getString("latestTimetableUpdate");
                        
                        resultList.add(new Person(-1, serverId, latestTimetableId, fullName, 
                                                  type, isVerified, timetable, latestTimetableUpdate));
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
            
            return resultList;
        }
        
        @Override
        protected void onPostExecute(ArrayList<Person> result) 
        {
            if (errorText != null)
            {
                Toast.makeText(FindTimetableActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                // The rest of the function can (no, /should/) continue even in case of error.
            }
            
            for (Person person : result)
                foundTimetablesAdapter.add(person);

            // Hide progress spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.findPersonSpinner);
            spinner.setVisibility(View.GONE);
            View view = findViewById(R.id.findPersonSpacerView);
            view.setVisibility(View.GONE);
            // Show results list
            ListView listView = (ListView)findViewById(R.id.findPersonLV);
            listView.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Add a new timetable.
     */
    public void addTimetableCbk(View v)
    {
        if (myPersonLiteId == -1)
        {
            String errorText = "You have to log in before you can do this!";
            Toast.makeText(this, errorText, Toast.LENGTH_LONG).show();
            Log.i(TAG, errorText);
        }
        else if (myEmailWasVerified)
        {
            Intent myIntent = new Intent(this, AddPersonActivity.class);
            startActivity(myIntent);
            
            // If the user successfully adds a person - they will want to return to the main screen
            // so get rid of this current find timetable activity from the stack.
            // If the user doesn't successfully add a person this is not very nice, but it seems less
            // likely and less painful.
            finish();
        }
        else
        {
            // Start a thread to check if the user's email is verified. The message in the button
            // doesn't say that, it would seem a little big-brotherish I feel.
            
            // Disable 'add' button
            Button addPersonBtn = (Button)findViewById(R.id.findPersonAddPersonBtn);
            addPersonBtn.setText("Please wait...");
            addPersonBtn.setEnabled(false);
            
            // Have to pass "this" because I want a callback once it's done.
            Globals.CheckIsUserVerified checker = new Globals.CheckIsUserVerified(this);
            checker.execute(myEmailAddress, myPasswordHash);
        }
    }

    /**
     * This is called from the CheckIsUserVerified thread after it's done.
     */
    public void checkIsUserVerifiedCompleted(int result, String errorText)
    {
        // Reset the 'add' button to normal, in case of errors below.
        Button addPersonBtn = (Button)findViewById(R.id.findPersonAddPersonBtn);
        addPersonBtn.setText("Add New Person");
        addPersonBtn.setEnabled(true);
        
        if (result == -1)
        {
            Toast.makeText(this, errorText, Toast.LENGTH_LONG).show();
            Log.e(TAG, errorText);
        }
        else if (result == 0)
        {
            errorText = "You have to verify your email before you can do this!";
            Toast.makeText(this, errorText, Toast.LENGTH_LONG).show();
            Log.i(TAG, errorText);
        }
        else
        {
            // This is hard to do in Globals.
            SharedPreferences.Editor prefEditor = preferences.edit();
            prefEditor.putBoolean("myEmailWasVerified", true);
            prefEditor.apply();
            myEmailWasVerified = true;
            
            Intent myIntent = new Intent(this, AddPersonActivity.class);
            startActivity(myIntent);
            
            // If the user successfully adds a person - they will want to return to the main screen
            // so get rid of this current find timetable activity from the stack.
            // If the user doesn't successfully add a person this is not very nice, but it seems less
            // likely and less painful.
            finish();
        }
    }
}
