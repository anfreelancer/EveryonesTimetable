package com.luteapp.everyonestimetable;

import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.os.AsyncTask;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import androidx.legacy.app.ActionBarDrawerToggle;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends AppCompatActivity
{
    static final String TAG = "EveryonesTimetable";
    static Context context;
    // Need this because onCreate() will rerun on orientation changes
    static boolean appStartedAtLeastOnce = false;
    SQLiteAdapter sqliteAdapter;
    SharedPreferences preferences;
    // The row ID for the user in sqlite or one of the special cases (-1/-2)
    int myPersonLiteId;
    String myEmailAddress;
    String myPasswordHash;
    Boolean myEmailWasVerified;
    // Information about the school
    static School mySchool;
    // This lets me populate a listview with nicely formatted results
    FoundPersonAdapter foundTimetablesAdapter;
    // Code to catch the preview activity returning
    static int TIMETABLE_PREVIEW_REQUEST = 0;
    // Need to keep this so that I can do something with that person when
    // the preview activity returns
    Person personBeingPreviewed;
    // This set of variables is to facilitate different registration flows
    // (specifically taking over a timetable, which requires a preview)
    String registeringEmail;
    String registeringFullName;
    String registeringType;
    String registeringPlainPassword;
    // The navigation drawer
    ListView leftDrawer;
    NavigationAdapter navigationAdapter;
    DrawerLayout drawerLayout;
    // What screen am I on? This is needed because I'm not using separate activities for this stuff
    enum SCREEN {PICK_SCHOOL, IDENTIFY_YOURSELF, FINDING_EMAIL, LOGIN, REGISTER, REGISTER_PICK_YOURSELF, MAIN};
    SCREEN currentScreen;
    ActionBarDrawerToggleCompat drawerToggle;
    
    /**
     * Decide whether to show the main UI or the login/registration screens.
     */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "Restore debug: onCreate()");
        MainActivity.context = this;

        sqliteAdapter = new SQLiteAdapter(this);
        
        preferences = getSharedPreferences("appPreferences", MODE_PRIVATE);
        myPersonLiteId = preferences.getInt("myPersonLiteId", -2);
        if (myPersonLiteId == -2) // Setup not done
        {
            showScreen(SCREEN.PICK_SCHOOL);
            new GetSchoolsListTask().execute();
        }
        else
        {
            myEmailAddress = preferences.getString("myEmailAddress", "shouldnt@get.this");
            myPasswordHash = preferences.getString("myPasswordHash", "shouldntgetthis");
            myEmailWasVerified = preferences.getBoolean("myEmailWasVerified", false);
            try
            {
                JSONObject mySchoolJson = new JSONObject(preferences.getString("mySchoolJson", ""));
                mySchool = new School(mySchoolJson);
            }
            catch (JSONException e)
            {
                Log.e(TAG, "This should never happen in Main!");
                e.printStackTrace();
            }
            
            showScreen(SCREEN.MAIN);
            setupActionBarAndDrawer();
            
            if (!appStartedAtLeastOnce)
            {
                // Slide the drawer out so users know it exists:
                drawerLayout.openDrawer(Gravity.LEFT);
                
                // And start updating the timetables
                updateAllTimetables();
                appStartedAtLeastOnce = true;
            }
        }
    }
    
    /**
     * Slide the drawer in and out when the action bar is pressed.
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item)
    {
        if (currentScreen == SCREEN.MAIN)
        {
            if (!drawerLayout.isDrawerOpen(Gravity.LEFT))
                drawerLayout.openDrawer(Gravity.LEFT);
            else
                drawerLayout.closeDrawer(Gravity.LEFT);
        }
        
        return true;
    }
    
    /**
     * Need this in order to get the cute animated menu icon to the left of the action bar icon.
     */
    private class ActionBarDrawerToggleCompat extends ActionBarDrawerToggle
    {
        public ActionBarDrawerToggleCompat(Activity mActivity, DrawerLayout mDrawerLayout)
        {
            super(mActivity, mDrawerLayout,
                    R.drawable.ic_action_navigation_drawer,
                    R.string.drawer_open, R.string.drawer_close);
        }
    }
    /**
     * For the same purpose as the ActionBarDrawerToggleCompat above.
     */
    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
        
        if (currentScreen == SCREEN.MAIN)
        {
            // Must be called from onPostCreate or else it causes annoying issues on relayout.
            drawerToggle.syncState();
        }
    }  
    
    /**
     * Connect the drawer to its data source and onclick listener.
     */
    void setupActionBarAndDrawer()
    {
        ActionBar actionBar = getSupportActionBar();
        actionBar.setIcon(R.drawable.ic_launcher);
        actionBar.setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
        
        leftDrawer = (ListView)findViewById(R.id.leftDrawerLV);
        navigationAdapter = NavigationAdapter.getNavigationAdapter(this);
        leftDrawer.setAdapter(navigationAdapter);
        leftDrawer.setOnItemClickListener(new DrawerItemClickListener());
        drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
        
        drawerToggle = new ActionBarDrawerToggleCompat(this, drawerLayout);
        drawerLayout.setDrawerListener(drawerToggle);
    }
    
    /**
     * When one of the items in the navigation drawer on the left is clicked. 
     */
    private class DrawerItemClickListener implements ListView.OnItemClickListener 
    {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) 
        {
            if (position == 0) // Update timetables
                updateAllTimetables();
            else if (position == 1) // Log out
                startActivity(new Intent(MainActivity.this, ShopActivity.class));
            else if (position == 2) // Log out
                logOut();
            else if (position == 3) // About
                showAboutWindow();
        }
    }
    
    /**
     * Because I need to trigger execution of UpdateAllTimetablesTask from more than one place
     * and it requires some setup before it's called.
     */
    void updateAllTimetables()
    {
        // If the user is not logged in and has no timetables added:
        if (myPersonLiteId == -1)
            return;
        
        NavigationItemAdapter updateMenuItem = navigationAdapter.getItem(0);
        updateMenuItem.isWorking = true;
        updateMenuItem.title = "Updating timetables...";
        navigationAdapter.notifyDataSetChanged();
        
        // Start thread to update timetables
        new UpdateAllTimetablesTask().execute(updateMenuItem);
    }
    
    /**
     * Update all the timetables in sqlite.
     */
    private class UpdateAllTimetablesTask extends AsyncTask<NavigationItemAdapter,NavigationItemAdapter,NavigationItemAdapter> 
    {
        String errorText = "";
        JSONObject updatesJson;
        
        @SuppressWarnings("deprecation")
        public NavigationItemAdapter doInBackground(NavigationItemAdapter... updateMenuItem) 
        {
            // Get a list of sqlite svrPersonIDs
            sqliteAdapter.open();
            ArrayList<Person> availableTimetables = sqliteAdapter.getListOfTimetables(-1);
            sqliteAdapter.close();
            
            // Make a list of the people and timetable versions I currently have
            String currentTimetables = "";
            for (int i = 0; i < availableTimetables.size(); i++)
            {
                currentTimetables += availableTimetables.get(i).serverId + ":" + 
                                     availableTimetables.get(i).latestTimetableId;
                if (i + 1 < availableTimetables.size())
                    currentTimetables += ";";
            }
            
            String command = "getTimetableUpdatesFor&userTimetablePairs=" + URLEncoder.encode(currentTimetables);
            String result;
            
            try
            {
                result = Globals.queryServer(command);
                
                updatesJson = new JSONObject(result);
                boolean querySucceeded = updatesJson.getBoolean("success");
                if (querySucceeded)
                {
                    //Log.d(TAG, updatesJson.toString());
                    // Yey, continue in onPostExecute()
                }
                else
                    errorText = Globals.formatServerErrorStack(updatesJson);
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
            
            return updateMenuItem[0];
        }
        
        /**
         * If any timetables have been updated - show a toast, otherwise just
         * change the menu button text back to normal and reenable that button.
         */
        @SuppressLint("InflateParams")
        public void onPostExecute(NavigationItemAdapter updateMenuItem) 
        {
            String updatedFor = "";
            int numTimetablesUpdated = 0;
            
            if (errorText.isEmpty())
            {
                try
                {
                    JSONArray people = updatesJson.getJSONArray("contents");
                    // For each person in the list of updated timetables 
                    for (int i = 0; i < people.length(); i++)
                    {
                        numTimetablesUpdated++;
                        
                        // Get the server IDs for the person and timetable, since the response won't
                        // have the local IDs, then get the local IDs based on the server IDs.
                        JSONObject person = people.getJSONObject(i);
                        int svrPersonId = person.getInt("personId");
                        int svrTimetableId = person.getInt("timetableId");
                        JSONArray jsonTimetable = person.getJSONObject("timetable").getJSONArray("timePeriods");
                        ArrayList<ArrayList<Period>> timetable = Period.makeTimetableFromJson(jsonTimetable);
                        
                        if (i > 0)
                            updatedFor += ", ";
                        
                        // Save the updated timetable in sqlite, and update the notification string 
                        sqliteAdapter.open();
                        updatedFor += sqliteAdapter.getPersonNameByServerId(svrPersonId);
                        int litePersonId = sqliteAdapter.getPersonId(svrPersonId);
                        sqliteAdapter.saveTimetable(litePersonId, svrTimetableId, timetable);
                        sqliteAdapter.close();
                        
                        Log.d(TAG, "Updated timetable for svrperson " + svrPersonId + ", svrtimetable " + svrTimetableId);
                    }
                }
                catch(JSONException e)
                {
                    errorText = "Got bad result from server";
                    e.printStackTrace();
                }
            }
            
            if (!errorText.isEmpty())
            {
                Log.e(TAG, errorText);
            }
            
            if (numTimetablesUpdated > 0)
            {
                String intro;
                if (numTimetablesUpdated == 1)
                    intro = "Updated timetable for ";
                else
                    intro = "Updated timetables for: ";
                Toast.makeText(MainActivity.this, intro + updatedFor, Toast.LENGTH_LONG).show();
            }
            
            updateMenuItem.isWorking = false;
            updateMenuItem.title = "Update timetables";
            navigationAdapter.notifyDataSetChanged();
            
            drawerLayout.closeDrawer(Gravity.LEFT);
        }
    }
    
    /**
     * Clear the login info, and clear timetables too
     */
    public void logOut()
    {
        preferences.edit().remove("myPersonLiteId").commit();
        preferences.edit().remove("myEmailAddress").commit();
        preferences.edit().remove("myPasswordHash").commit();
        preferences.edit().remove("myEmailWasVerified").commit();
        finish();
        
        sqliteAdapter.open();
        sqliteAdapter.deleteEverything();
        sqliteAdapter.close();
    }

    @Override
    public void onStart()
    {
        super.onStart();
        Log.d(TAG, "Restore debug: onStart()");
    }

    @Override
    public void onPause()
    {
        super.onPause();
        Log.d(TAG, "Restore debug: onPause()");
    }

    @Override
    public void onDestroy()
    {
        super.onDestroy();
        Log.d(TAG, "Restore debug: onDestroy()");
    }

    @Override
    public void onStop()
    {
        super.onStop();
        Log.d(TAG, "Restore debug: onStop()");
    }

    @Override
    public void onRestart()
    {
        super.onRestart();
        Log.d(TAG, "Restore debug: onRestart()");
    }

    /**
     * The timetable needs to be refreshed in case I am coming back from another
     * activity such as adding a timetable.
     */
    @Override
    public void onResume() 
    {
        super.onResume();
        Log.d(TAG, "Restore debug: onResume()");
        if (currentScreen == SCREEN.MAIN) // In case I just added or deleted a timetable
            refreshTimetableList();
    }

    /**
     * Registration/Login:
     * Use the web service to get the list of schools to populate the dropdown list.
     * I don't want to hardcode this list here because hopefully many schools will be added
     * and I won't want to release a new version (and make everyone update) every time.
     */
    private class GetSchoolsListTask extends AsyncTask<Void, Void, Void> 
    {
        String errorText = "";
        ArrayList<School> schoolsList = new ArrayList<School>();
        
        public Void doInBackground(Void... v)
        {
            String command = "getListOfSchools";
            String result;
            
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONArray schools = json.getJSONArray("contents");
                    for (int i = 0; i < schools.length(); i++)
                        schoolsList.add(new School(schools.getJSONObject(i)));
                    
                    if (schoolsList.size() == 0)
                        errorText = "Server didn't return any schools in the list.";
                }
                else
                    errorText = Globals.formatServerErrorStack(json);
            }
            catch (NumberFormatException e)
            {
                errorText = "Got bad result from server, the periods string was broken?";
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
            
            return null;
        }
        
        /**
         * In case of an error - show the user a message and quit, because I don't want to add
         * a "try again" button and they will need to go fix their internet anyway and there's nothing
         * else that can be done until this is finished.
         */
        @SuppressLint("InflateParams")
        public void onPostExecute(Void v) 
        {
            if (!errorText.isEmpty())
            {
                errorText += "\n\nYou need a working inetrnet connection to do the initial setup." +
                             "\n\nPlease run the app again once you have it.";
                Log.e(TAG, errorText);
                
                AlertDialog alertDialog = new AlertDialog.Builder(context).create();
                alertDialog.setMessage(errorText);
                alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() 
                                      {public void onClick(DialogInterface dialog, int which) {MainActivity.this.finish();}});
                alertDialog.setCancelable(false);
                alertDialog.show();
                return;
            }
            
            // Populate the schools spinner
            Spinner spinner = (Spinner) findViewById(R.id.pickSchoolSchoolDropdown);
            ArrayAdapter<School> adapter = new ArrayAdapter<School>(MainActivity.this, android.R.layout.simple_spinner_item, schoolsList);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
            
            // Hide and show widgets
            spinner.setVisibility(View.VISIBLE);
            ProgressBar progressBar = (ProgressBar)findViewById(R.id.pickSchoolSpinner);
            progressBar.setVisibility(View.GONE);
            Button button = (Button)findViewById(R.id.connectNextBtn);
            button.setEnabled(true);
        }
    }
    
    /**
     * Registration/Login:
     * 
     */
    public void schoolSelectedCbk(View v)
    {
        Spinner spinner = (Spinner) findViewById(R.id.pickSchoolSchoolDropdown);
        mySchool = (School)spinner.getSelectedItem();
        
        showScreen(SCREEN.IDENTIFY_YOURSELF);
    }
    
    /**
     * Registration/Login:
     * Check if the email address follows the rules for the school.
     */
    boolean emailIsValid(String email)
    {
        for (int i = 0; i < mySchool.emailServers.length; i++)
        {
            if (email.toLowerCase().matches(mySchool.emailServers[i]))
                return true;
        }
        return false;
    }
    
    /**
     * Registration/Login:
     * Begin the process of figuring out whether the user is registered.
     * i.e. Make some changes to the View and start the LoginTask
     */
    public void checkIfRegisteredCbk(View v)
    {
        if(!BillingClientSetup.isUpgraded(getApplicationContext())) return;
        TextView emailAddrFld = (TextView)findViewById(R.id.connectEmailAddrFld);
        String email = emailAddrFld.getText().toString();
        
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches())
        {
            Globals.showNotAllowed(this, "You have to put in a valid email address");
        }
        else if (!emailIsValid(email))
        {
            String emailServers = "";
            for (int i = 0; i < mySchool.emailServers.length; i++)
            {
                if (i > 0)
                    emailServers += ", or ";
                emailServers += "@" + mySchool.emailServers[i];
            }
            Globals.showNotAllowed(this, "Your have to use a " + mySchool.name + " email address (" + emailServers + ")");
        }
        else
        {
            // Hide the buttons and privacy policy link
            Button nextBtn = (Button)findViewById(R.id.connectNextBtn);
            nextBtn.setVisibility(View.GONE);
            TextView privacyPolicyLbl = (TextView)findViewById(R.id.connectPrivacyPolicyLbl);
            privacyPolicyLbl.setVisibility(View.GONE);
            Button skipRegistrationBtn = (Button)findViewById(R.id.connectSkipRegistrationBtn);
            skipRegistrationBtn.setVisibility(View.GONE);
            
            // Disable the email field
            emailAddrFld.setEnabled(false);
            emailAddrFld.setFocusable(false);
            
            // And make sure the keyboard dissapears too
            InputMethodManager imm = (InputMethodManager)getSystemService(
                                                            Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(emailAddrFld.getWindowToken(), 0);
            
            // Show spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.connectSpinner);
            spinner.setVisibility(View.VISIBLE);
            
            currentScreen = SCREEN.FINDING_EMAIL;
            
            // Start thread to check if registered
            new CheckIfRegisteredTask().execute(emailAddrFld.getText().toString());
        }
    }
    
    /**
     * Registration/Login:
     * Use the web service to either check whether the user exists.
     */
    private class CheckIfRegisteredTask extends AsyncTask<String, Integer, Boolean> 
    {
        String errorText = null;
        
        /**
         * credentials[0] is the email address
         * 
         * Returns whether the username is registered or not (not in case of error)
         */
        @SuppressWarnings("deprecation")
        public Boolean doInBackground(String... credentials) 
        {
            String user = credentials[0];
            String command = "checkIfUserExists" + 
                             "&email=" + URLEncoder.encode(user) + 
                             "&schoolId=" + Integer.toString(mySchool.serverId);
            String result;
            
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONObject contents = json.getJSONObject("contents");
                    if (contents.getBoolean("doesExist"))
                        return true;
                    else
                    {
                        // User does not exist
                        return false;
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
         * Now that I know whether the user is registered or not: either
         * show an error and go back to the previous screen or go to the login
         * screen (if already registered) or to the registration screen (if not yet registered).
         */
        @SuppressLint("InflateParams")
        public void onPostExecute(Boolean userExists) 
        {
            TextView setupEmailAddrFld = (TextView)findViewById(R.id.connectEmailAddrFld);
            String emailAddr = setupEmailAddrFld.getText().toString();
            
            if (errorText != null)
            {
                Toast.makeText(MainActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                // Show the buttons
                Button nextBtn = (Button)findViewById(R.id.connectNextBtn);
                nextBtn.setVisibility(View.VISIBLE);
                TextView privacyPolicyLbl = (TextView)findViewById(R.id.connectPrivacyPolicyLbl);
                privacyPolicyLbl.setVisibility(View.VISIBLE);
                Button skipRegistrationBtn = (Button)findViewById(R.id.connectSkipRegistrationBtn);
                skipRegistrationBtn.setVisibility(View.VISIBLE);
                
                // Reenable the email field
                setupEmailAddrFld.setEnabled(true);
                setupEmailAddrFld.setFocusable(true);
                setupEmailAddrFld.setFocusableInTouchMode(true);
                
                // Hide spinner
                ProgressBar spinner = (ProgressBar)findViewById(R.id.connectSpinner);
                spinner.setVisibility(View.GONE);
                
                currentScreen = SCREEN.IDENTIFY_YOURSELF;
                
                return;
            }
            
            if (userExists)
            {
                showScreen(SCREEN.LOGIN);
                
                TextView loginEmailAddrLbl = (TextView)findViewById(R.id.loginHiddenEmailAddrLbl);
                loginEmailAddrLbl.setText(emailAddr);
            }
            else
            { 
                showScreen(SCREEN.REGISTER);
                
                TextView registerEmailAddrLbl = (TextView)findViewById(R.id.registerHiddenEmailAddrLbl);
                registerEmailAddrLbl.setText(emailAddr);
            }
        }
    }
    
    /**
     * Registration/Login:
     * Allow the user to stay unregistered and proceed to the main view. 
     * Edit functionality will be disabled but they will be able to view timetables.
     */
    @SuppressLint("InflateParams")
    public void skipRegistrationCbk(View v)
    {
        myPersonLiteId = -1;
        SharedPreferences.Editor prefEditor = preferences.edit();
        prefEditor.putInt("myPersonLiteId", myPersonLiteId);
        String mySchoolJson = "";
        try
        {
            mySchoolJson = mySchool.makeJson().toString();
        }
        catch (JSONException e)
        {
            Log.e(TAG, "This should never happen in Main/Skip!");
            e.printStackTrace();
        }
        prefEditor.putString("mySchoolJson", mySchoolJson);
        prefEditor.apply();
        
        showScreen(SCREEN.MAIN);
        setupActionBarAndDrawer();
        refreshTimetableList();
    }
    
    /**
     * Registration:
     * Process request to register a new user (callback from register.xml)
     */
    public void registerBtnCbk(View v)
    {
        TextView textView;
        
        textView = (TextView)(findViewById(R.id.registerNameFld));
        String name = textView.getText().toString();
        textView = (TextView)(findViewById(R.id.registerPassword1Fld));
        String password1 = textView.getText().toString();
        textView = (TextView)(findViewById(R.id.registerPassword2Fld));
        String password2 = textView.getText().toString();
        String studentOrProf;
        RadioButton radioButton = (RadioButton)findViewById(R.id.registerStudentRdo);
        if (radioButton.isChecked())
            studentOrProf = "1";
        else
            studentOrProf = "2";
        
        if (name.isEmpty())
        {
            Globals.showNotAllowed(this, "Please enter your full name. Without your name " +
                                         "there'll be no way of telling which timetable is yours " +
                                         "and which is someone else's!");
        }
        else if (password1.isEmpty())
        {
            Globals.showNotAllowed(this, "Please enter a password, so that you can share " +
                                         "timetable updates with other people.");
        }
        else if (password2.compareTo(password1) != 0)
        {
            Globals.showNotAllowed(this, "The first and second password must be the same.");
        }
        else
        {
            // Disable/hide the register button
            Button registerBtn = (Button)findViewById(R.id.registerBtn);
            registerBtn.setVisibility(View.GONE);
            
            // Show spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.registerSpinner);
            spinner.setVisibility(View.VISIBLE);
            
            TextView registerEmailAddrLbl = (TextView)findViewById(R.id.registerHiddenEmailAddrLbl);
            String emailAddr = registerEmailAddrLbl.getText().toString();

            // Make sure the keyboard dissapears, so can see toast will error if it happens 
            InputMethodManager imm = (InputMethodManager)getSystemService(
                                                            Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
            
            // Start thread to register online
            new FindCandidateTakeoverTimetablesTask()
                                .execute(emailAddr, name, studentOrProf, password1);
        }
    }

    /**
     * This only exists because I wanted to override getView() so that each row in the list
     * would show more than one string (see find_timetable_row.xml).
     * A similar class is in FindTimetableActivity.
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
                        View row = (View)button.getParent().getParent();
                        Person foundPerson = (Person)row.getTag();
                        
                        // Save these for when the preview activity terminates
                        personBeingPreviewed = foundPerson;
                        
                        // Disable the "neither of these" button. Unfortunately the
                        // "yes" from the preview activity won't be received until
                        // a second or so after that activity exits and this particular
                        // button is in the same place as the yes/no buttons.
                        Button noneBtn = (Button)findViewById(R.id.pickTimetableNoneBtn);
                        noneBtn.setEnabled(false);
                        
                        // Start the preview activity
                        Intent myIntent = new Intent(MainActivity.this, ShowTimetableActivity.class);
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
            // Server findTakeoverCandidates should never return one that's already verified
            // but I just copy-pasted this block from FindTimetableActivity.
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
     * Registration:
     * If a list of timetables to take over is presented but the user doesn't want any
     * of them - just proceed with a simple registration.
     */
    public void registerNoTakeoverCbk(View v)
    {
        // Start another thread to finish the registration
        new RegisterTask().execute(registeringEmail, registeringFullName, registeringType, 
                                   registeringPlainPassword);
    }
    
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
                // This is my timetable, take it over
                Log.d(TAG, "Taking over timetable");
                
                // On slow connections this RegisterTask seems to take way too long, and
                // the user will be tempted to repeat the register request. So just clear the 
                // list of results, since we don't need it any more.
                foundTimetablesAdapter.clear();
                
                // Use the "Neither of these..." button as a "please wait" message
                Button noneBtn = (Button)findViewById(R.id.pickTimetableNoneBtn);
                noneBtn.setText("Completing registration...");
                
                new RegisterTask().execute(registeringEmail, 
                                           personBeingPreviewed.fullName, 
                                           Integer.toString(personBeingPreviewed.type), 
                                           registeringPlainPassword, 
                                           Integer.toString(personBeingPreviewed.serverId));
            }
            else
            {
                // Reenable the button
                Button noneBtn = (Button)findViewById(R.id.pickTimetableNoneBtn);
                noneBtn.setEnabled(true);
            }
        }
    }
    
    /**
     * Registration:
     * Check if any timetables exist in the database that might be for the user currently
     * registering (same name). If so - offer to take ownership of one, else skip to
     * fresh registration.
     */
    private class FindCandidateTakeoverTimetablesTask extends AsyncTask<String, Void, ArrayList<Person>> 
    {
        String errorText = null;
        String email;
        String fullName;
        String typeString;
        String plainPassword;
        
        /**
         * credentials[0] is the email address
         * credentials[1] is the full name
         * credentials[2] is the type (1 for student, 2 for prof)
         * credentials[3] is the plain text password
         * 
         * Returns success or fail boolean, error stored in member variable.
         */
        @SuppressWarnings("deprecation")
        public ArrayList<Person> doInBackground(String... credentials) 
        {
            ArrayList<Person> resultList = new ArrayList<Person>();
            
            email = credentials[0];
            fullName = credentials[1];
            typeString = credentials[2];
            plainPassword = credentials[3];
            
            String command = "findTakeoverCandidates" +
                    "&name=" + URLEncoder.encode(fullName) +
                    "&schoolId=" + mySchool.serverId +
                    "&type=" + URLEncoder.encode(typeString);
            
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
                        ArrayList<ArrayList<Period>> timetable = Globals.getTimetableForPerson(latestTimetableId);
                        String latestTimetableUpdate = person.getString("latestTimetableUpdate");
                        
                        resultList.add(new Person(-1, serverId, latestTimetableId, fullName, 
                                                  type, false, timetable, latestTimetableUpdate));
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
        
        /**
         * Now either show an error 
         * or show the list of candidate timetables 
         * or if none were found - continue registration.
         */
        @SuppressLint("InflateParams")
        public void onPostExecute(ArrayList<Person> result) 
        {
            if (errorText != null)
            {
                Toast.makeText(MainActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                // Reenable/show the register button
                Button registerBtn = (Button)findViewById(R.id.registerBtn);
                registerBtn.setVisibility(View.VISIBLE);
                
                // Hide spinner
                ProgressBar spinner = (ProgressBar)findViewById(R.id.registerSpinner);
                spinner.setVisibility(View.GONE);
            }
            else if (result.size() == 0) // No timetables found
            {
                Log.d(TAG, "No takeover candidates found");
                
                // Start another thread to finish the registration
                new RegisterTask().execute(email, fullName, typeString, plainPassword);
            }
            else // Show list of potential timetables
            {
                Log.d(TAG, "Found some takeover candidates");
                
                registeringEmail = email;
                registeringFullName = fullName;
                registeringType = typeString;
                registeringPlainPassword = plainPassword;
                
                showScreen(SCREEN.REGISTER_PICK_YOURSELF);
                
                // This is the list for results
                ListView listView = (ListView)findViewById(R.id.pickTimetableLV);
                // This adapter is used to connect the list of results found with
                // a the listview above and also a custom view for each row.
                foundTimetablesAdapter = new FoundPersonAdapter(MainActivity.this, R.layout.find_timetable_row);
                listView.setAdapter(foundTimetablesAdapter);
                
                for (Person person : result)
                    foundTimetablesAdapter.add(person);
            }
        }
    }
    
    /**
     * Registration:
     * Use the web service to register the user.
     */
    private class RegisterTask extends AsyncTask<String, Void, Boolean> 
    {
        String errorText = null;
        String email;
        int svrPersonId;
        int svrTimetableId;
        String fullName;
        String typeString;
        int type;
        String hashedPassword;
        // vs Takeover existing person
        boolean doingSimpleRegister = true;
        
        /**
         * credentials[0] is the email address
         * credentials[1] is the full name
         * credentials[2] is the type (1 for student, 2 for prof)
         * credentials[3] is the plain text password
         * credentials[4] is an optional integer server ID of an unregistered person to take over
         * 
         * Returns success or fail boolean, error stored in member variable.
         */
        @SuppressWarnings("deprecation")
        public Boolean doInBackground(String... credentials) 
        {
            email = credentials[0];
            fullName = credentials[1];
            typeString = credentials[2];
            type = Integer.parseInt(typeString);
            String plainPassword = credentials[3];
            hashedPassword = Globals.makeSHA512Hash("EveryonesTimetableSalt" + plainPassword);

            if (credentials.length == 5)
                doingSimpleRegister = false;
            
            String command;
            
            // Registering and taking over an existing user are actually different
            // server calls but it's convenient to use this AsyncTask for both cases.
            if (doingSimpleRegister)
            {
                command = "registerUser" +
                          "&email=" + URLEncoder.encode(email) +
                          "&schoolId=" + Integer.toString(mySchool.serverId) +
                          "&fullName=" + URLEncoder.encode(fullName) +
                          "&passwordHash=" + hashedPassword +
                          "&type=" + URLEncoder.encode(typeString);
            }
            else
            {
                command = "takeOverThisPerson&personId=" + credentials[4] +
                          "&email=" + URLEncoder.encode(email) +
                          "&passwordHash=" + hashedPassword;
            }
            
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
                        errorText = "Failed to register: missing or bad new user id";
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
                Toast.makeText(MainActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                /* This doesn't work if !doingSimpleRegister
                
                // Reenable/show the register button
                Button registerBtn = (Button)findViewById(R.id.registerBtn);
                registerBtn.setVisibility(View.VISIBLE);
                
                // Hide spinner
                ProgressBar spinner = (ProgressBar)findViewById(R.id.registerSpinner);
                spinner.setVisibility(View.GONE);*/
                
                return;
            }
            
            // Remind the user that the registration isn't complete until they verify their email
            Globals.showAlert(MainActivity.this, "You will receive an email with a link you'll need to click " +
                                                 "to complete your registration.");
            
            loginOrRegisterDone(svrPersonId, svrTimetableId, email, fullName, 
                                type, hashedPassword, false);
        }
    }
    
    /**
     * Login: Callback for the log in button.
     */
    public void loginBtnCbk(View v)
    {
        TextView emailAddrLbl = (TextView)(findViewById(R.id.loginHiddenEmailAddrLbl));
        String emailAddr = emailAddrLbl.getText().toString();
        EditText passwordView = (EditText)(findViewById(R.id.loginPasswordFld));
        String plainPassword = passwordView.getText().toString();
        
        if (plainPassword.isEmpty())
        {
            Globals.showNotAllowed(this, "Please enter your password!");
        }
        else
        {
            // Disable/hide the buttons
            Button loginBtn = (Button)findViewById(R.id.loginLoginBtn);
            loginBtn.setVisibility(View.GONE);
            Button loginForgotBtn = (Button)findViewById(R.id.loginForgotBtn);
            loginForgotBtn.setVisibility(View.GONE);
            
            // Disable the email field
            passwordView.setEnabled(false);
            passwordView.setFocusable(false);
            
            // And make sure the keyboard dissapears too
            InputMethodManager imm = (InputMethodManager)getSystemService(
                                                            Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(passwordView.getWindowToken(), 0);
            
            // Show spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.loginSpinner);
            spinner.setVisibility(View.VISIBLE);
            
            // Start thread to register online
            new LoginTask().execute(emailAddr, plainPassword);
        }
    }
    
    /**
     * Login: Use the web service to log in the user (basically just verify the credentials).
     */
    private class LoginTask extends AsyncTask<String, Void, Boolean> 
    {
        String errorText = null;
        String email;
        int svrPersonId;
        int svrTimetableId;
        String fullName;
        int type;
        String hashedPassword;
        Boolean emailVerified;
        
        /**
         * credentials[0] is the email address
         * credentials[1] is the plain text password
         * 
         * Returns whether the credentials were valid.
         */
        @SuppressWarnings("deprecation")
        public Boolean doInBackground(String... credentials) 
        {
            email = credentials[0];
            String plainPassword = credentials[1];
            hashedPassword = Globals.makeSHA512Hash("EveryonesTimetableSalt" + plainPassword);
            
            String command = "checkCredentialsAndGetUserInfo" +
                    "&email=" + URLEncoder.encode(email) +
                    "&passwordHash=" + hashedPassword;
            
            String result;
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
                        fullName = contents.getString("fullName");
                        type = contents.getInt("type");
                        emailVerified = contents.getBoolean("emailVerified");
                        return true;
                    }
                    else
                        errorText = "Your password is incorrect";
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
         * or continue the login process.
         */
        public void onPostExecute(Boolean okSoFar) 
        {
            EditText passwordView = (EditText)(findViewById(R.id.loginPasswordFld));
            
            if (errorText != null)
            {
                Toast.makeText(MainActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                // Reenable/show the buttons
                Button loginBtn = (Button)findViewById(R.id.loginLoginBtn);
                loginBtn.setVisibility(View.VISIBLE);
                Button loginForgotBtn = (Button)findViewById(R.id.loginForgotBtn);
                loginForgotBtn.setVisibility(View.VISIBLE);
                
                // Reenable the email field
                passwordView.setEnabled(true);
                passwordView.setFocusable(true);
                passwordView.setFocusableInTouchMode(true);
                
                // Hide spinner
                ProgressBar spinner = (ProgressBar)findViewById(R.id.loginSpinner);
                spinner.setVisibility(View.GONE);
                
                return;
            }
            
            loginOrRegisterDone(svrPersonId, svrTimetableId, email, fullName, 
                                type, hashedPassword, emailVerified);
        }
    }
    
    int registrationPersonId;
    int registrationTimetableId;
    String registrationFullName;
    int registrationType;
    
    /**
     * Registration/Login:
     * Called after RegisterTask or LoginTask is completed successfully.
     */
    public void loginOrRegisterDone(int personId, int timetableId, String email, String fullName, 
                                    int type, String passwordHash, Boolean emailVerified)
    {
        Log.d(TAG, "loginOrRegisterDone(): " + String.valueOf(timetableId) + ", " + email + ", " + fullName + ", " + type + ", " + passwordHash);
        
        // Configure the app (sqlite and prefs) as if the login/register was complete.
        // It still has a chance to fail when I download the timetable immeditely after this,
        // so annoyingly I'll want to be passing all this data on to the end of that AsyncTask.
        registrationPersonId = personId;
        registrationTimetableId = timetableId;
        registrationFullName = fullName;
        registrationType = type;
        
        myEmailAddress = email;
        myPasswordHash = passwordHash;
        myEmailWasVerified = emailVerified;
        
        // Start thread to register online
        new DownloadMyTimetableTask().execute(timetableId);
    }
    
    /**
     * Registration/Login:
     * Final step: download the timetable for the user.
     */
    private class DownloadMyTimetableTask extends AsyncTask<Integer,Void,Void> 
    {
        String errorText = "";
        String mySchoolJson;
        
        /**
         * IDs[0] is the timetableId I want to download from the server.
         * 
         * Returns the timetable.
         */
        public Void doInBackground(Integer... IDs) 
        {
            Integer svrTimetableId = IDs[0];
            
            try
            {
                // Parse out the timetable
                ArrayList<ArrayList<Period>> timetable = Globals.getTimetableForPerson(svrTimetableId);
                
                // And insert it into SQLite
                sqliteAdapter.open();
                myPersonLiteId = sqliteAdapter.addPerson(registrationPersonId, registrationTimetableId, 
                                                         registrationFullName, registrationType);
                sqliteAdapter.saveTimetable(myPersonLiteId, svrTimetableId, timetable);
                sqliteAdapter.close();
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
            
            try
            {
                mySchoolJson = mySchool.makeJson().toString();
            }
            catch (JSONException e)
            {
                errorText = "Couldn't save school information";
                e.printStackTrace();
            }
            
            return null;
        }
        
        /**
         * Now possibly show an error and quit, because none of these errors are really recoverable.
         */
        @SuppressLint("InflateParams")
        public void onPostExecute(Void v) 
        {
            if (!errorText.isEmpty())
            {
                Globals.showAlert(MainActivity.this, errorText);
                Log.e(TAG, errorText);
                finish();
            }
            
            SharedPreferences.Editor prefEditor = preferences.edit();
            
            prefEditor.putInt("myPersonLiteId", myPersonLiteId);
            prefEditor.putString("myEmailAddress", myEmailAddress);
            prefEditor.putString("myPasswordHash", myPasswordHash);
            prefEditor.putBoolean("myEmailWasVerified", myEmailWasVerified);
            prefEditor.putString("mySchoolJson", mySchoolJson);
            prefEditor.apply();
            
            showScreen(SCREEN.MAIN);
            setupActionBarAndDrawer();
            refreshTimetableList();
        }
    }

    /**
     * Login: reset password button callback.
     */
    public void resetPasswordCbk(View v)
    {
        // Hide the login button
        Button loginBtn = (Button)findViewById(R.id.loginLoginBtn);
        loginBtn.setVisibility(View.GONE);
        // Report some progress to the user via the reset button
        Button loginForgotBtn = (Button)findViewById(R.id.loginForgotBtn);
        loginForgotBtn.setText("Please wait, sending reset email...");
        loginForgotBtn.setEnabled(false);
        
        TextView emailAddrLbl = (TextView)(findViewById(R.id.loginHiddenEmailAddrLbl));
        String emailAddr = emailAddrLbl.getText().toString();
        
        // Start thread to ask server to send reset instructions
        new ResetPasswordRequestTask().execute(emailAddr);
    }
    
    /**
     * Login: Use the web service to ask it to send a reset password email.
     */
    private class ResetPasswordRequestTask extends AsyncTask<String, Void, Void> 
    {
        String errorText = null;
        String email;
        
        /**
         * credentials[0] is the email address
         */
        @SuppressWarnings("deprecation")
        public Void doInBackground(String... credentials) 
        {
            email = credentials[0];
            
            String command = "sendResetPasswordEmail" +
                             "&email=" + URLEncoder.encode(email);
            
            String result = null;
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (!querySucceeded)
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
            
            return null;
        }
        
        public void onPostExecute(Void v) 
        {
            if (errorText != null)
            {
                Toast.makeText(MainActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
            }
            else
            {
                // Leave the reset button disabled
                Button loginForgotBtn = (Button)findViewById(R.id.loginForgotBtn);
                loginForgotBtn.setText("Password reset email sent.");
            }
            
            // Show the login button
            Button loginBtn = (Button)findViewById(R.id.loginLoginBtn);
            loginBtn.setVisibility(View.VISIBLE);
        }
    }
    
    /**
     * Populate the list of people other than me on the main screen.
     */
    public void refreshTimetableList()
    {
        // Get the list of people
        sqliteAdapter.open();
        ArrayList<Person> availableTimetables = sqliteAdapter.getListOfTimetables(myPersonLiteId);
        sqliteAdapter.close();

        // Sort the list alphabetically, that's the best I can think of:
        Collections.sort(availableTimetables);
        
        // And show them all in the appropriate list view 
        LinearLayout otherPeoplesTimetableButtons = (LinearLayout)findViewById(
                                                        R.id.otherPeoplesTimetableButtons);
        
        // But clear the contents first, in case this is not called the first time
        otherPeoplesTimetableButtons.removeAllViews();
        
        TextView message = new TextView(this);
        if (availableTimetables.size() == 0)
            message.setText("Frequently used timetables will show up here when you add them");
        else
        {
            for (int i = 0; i < availableTimetables.size(); i++)
            {
                Button button = new Button(this);
                button.setText(availableTimetables.get(i).fullName);
                // I will need the ID when the button is pressed
                button.setTag(availableTimetables.get(i).localId);
                button.setOnClickListener(showTimetableForCbk);
                otherPeoplesTimetableButtons.addView(button);
            }
            
            message.setText("More timetables will show up here when you add them");
        }
        message.setGravity(Gravity.CENTER);
        otherPeoplesTimetableButtons.addView(message);
    }
    
    /**
     * This is the callback for all the people's timetable buttons. The ID of the 
     * timetable is in the tag in the view.
     */
    Button.OnClickListener showTimetableForCbk = new Button.OnClickListener() 
    {
        @Override
        public void onClick(final View v) 
        {
            Button button = (Button)v;
            int localPersonDbId = (Integer)button.getTag();
            showTimetableFor(localPersonDbId);
        }
    };
    
    /**
     * Start the activity to show userId's timetable.
     */
    public void showTimetableFor(int localPersonDbId)
    {
        sqliteAdapter.open();
        String personName = sqliteAdapter.getPersonNameById(localPersonDbId);
        sqliteAdapter.close();
        
        Intent myIntent = new Intent(this, ShowTimetableActivity.class);
        myIntent.putExtra("userId", localPersonDbId);
        myIntent.putExtra("personName", personName);
        startActivity(myIntent);
    }
    
    /**
     * If my name is set - show my timetable, otherwise ask for my name.
     */
    @SuppressLint("InflateParams")
    public void myTimetableCbk(View v)
    {
        if (myPersonLiteId == -1)
        {
            // Registration skipped, but is required for this function, so go
            // back to the setup screen.
            showScreen(SCREEN.IDENTIFY_YOURSELF);
        }
        else
        {
            // Show my timetable
            showTimetableFor(myPersonLiteId);
        }
    }
    
    /**
     * Find someone's timetable online and add it.
     */
    public void findTimetableCbk(View v)
    {
        if(!BillingClientSetup.isUpgraded(getApplicationContext())) return;
        Intent myIntent = new Intent(this, FindTimetableActivity.class);
        startActivity(myIntent);
    }
    
    @SuppressLint("InflateParams")
    public void showAboutWindow()
    {
        // Inflate the about message contents
        View messageView = getLayoutInflater().inflate(R.layout.about, null, false);
 
        // When linking text, force to always use default color. This works
        // around a pressed color state bug.
        TextView textView = (TextView) messageView.findViewById(R.id.about_credits);
        int defaultColor = textView.getTextColors().getDefaultColor();
        textView.setTextColor(defaultColor);
 
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setIcon(R.drawable.ic_launcher);
        builder.setTitle(R.string.app_name);
        builder.setView(messageView);
        builder.create();
        builder.show();
    }

    @Override
    public void onBackPressed()
    {
        if (currentScreen == SCREEN.IDENTIFY_YOURSELF)
        {
            showScreen(SCREEN.PICK_SCHOOL);
            new GetSchoolsListTask().execute();
        }
        else if (currentScreen == SCREEN.LOGIN ||
                 currentScreen == SCREEN.REGISTER)
        {
            showScreen(SCREEN.IDENTIFY_YOURSELF);
        }
        else if (currentScreen == SCREEN.FINDING_EMAIL)
        {
            // Do nothing, it's too much work to cancel that task :)
        }
        else if (currentScreen == SCREEN.REGISTER_PICK_YOURSELF)
        {
            showScreen(SCREEN.REGISTER);
        }
        else if (currentScreen == SCREEN.MAIN)
        {
            super.onBackPressed();
        }
        else
            super.onBackPressed();
    }
    
    /**
     * Show the "Connect to Seneca Timetable Network" screen, asking user for their email.
     */
    @SuppressLint("InflateParams")
    public void showScreen(SCREEN newScreen)
    {
        LayoutInflater inflator = getLayoutInflater();
        View view;
        
        currentScreen = newScreen;
        
        if (newScreen == SCREEN.PICK_SCHOOL)
            view = inflator.inflate(R.layout.pick_school, null, false);
        else if (newScreen == SCREEN.IDENTIFY_YOURSELF)
        {
            view = inflator.inflate(R.layout.setup, null, false);
            TextView textView = (TextView) view.findViewById(R.id.connectTitle);
            textView.setText("Connect to the\n" + mySchool.name + "\nTimetable Network");
            textView = (TextView) view.findViewById(R.id.connectEnterEmailLbl);
            textView.setText("Enter your " + mySchool.name + " email address:");
        }
        else if (newScreen == SCREEN.LOGIN)
            view = inflator.inflate(R.layout.login, null, false);
        else if (newScreen == SCREEN.REGISTER)
            view = inflator.inflate(R.layout.register, null, false);
        else if (newScreen == SCREEN.REGISTER_PICK_YOURSELF)
            view = inflator.inflate(R.layout.pick_timetable, null, false);
        else //if (newScreen == SCREEN.MAIN)
            view = inflator.inflate(R.layout.main, null, false);

        view.startAnimation(AnimationUtils.loadAnimation(MainActivity.this, 
                android.R.anim.fade_in));
        setContentView(view);
    }
}
