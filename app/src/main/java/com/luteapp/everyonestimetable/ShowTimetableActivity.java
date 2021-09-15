package com.luteapp.everyonestimetable;

import java.net.URLEncoder;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.os.AsyncTask;
import android.os.Bundle;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import androidx.appcompat.widget.PopupMenu;
import androidx.appcompat.widget.PopupMenu.OnMenuItemClickListener;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewFlipper;

//Toast.makeText(this, "asd", Toast.LENGTH_SHORT).show();

/**
 * Activity to show a timetable, either in portrait (one day at a time) \
 * or in landscape (entire week) mode.
 */
public class ShowTimetableActivity extends Activity 
    implements Globals.CheckIsUserVerifiedCallback, OnMenuItemClickListener
{
    static final String TAG = "EveryonesTimetable";
    // Which person's timetable is being displayed
    int currentTimetablePersonId;
    // And that person's full timetable
    ArrayList<ArrayList<Period>> currentTimetable;
    // And their full name
    String personName;
    // For the left-right swipe gesture detection
    float lastXtouched;
    // How many pixels consitutes a swipe
    final float minSwipeLen = 10;
    // Flippers for the title in the middle and the timetable content 
    // Only used in Day view (portrait).
    ViewFlipper titleFlipper;
    ViewFlipper tableFlipper;
    // To access the local database
    SQLiteAdapter sqliteAdapter;
    boolean amInEditMode = false;
    SharedPreferences preferences;
    // The row ID for the user in sqlite
    int myPersonLiteId;
    String myEmailAddress;
    String myPasswordHash;
    Boolean myEmailWasVerified;
    // Preview activity finished with a yes
    static int PREVIEW_RESULT_YES = RESULT_FIRST_USER;
    // List of course codes to use as autocomplete suggestions
    ArrayList<String> courseCodeCache;
    // List of room numbers to use as autocomplete suggestions
    ArrayList<String> roomNumberCache;
    
    /**
     * Show the timetable (from cache) for the person requested.
     */
    @SuppressWarnings("unchecked")
    @Override
    public void onCreate(Bundle savedInstanceState) 
    {
        super.onCreate(savedInstanceState);
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.view_timetable);
        
        preferences = getSharedPreferences("appPreferences", MODE_PRIVATE);
        myPersonLiteId = preferences.getInt("myPersonLiteId", -2);
        myEmailAddress = preferences.getString("myEmailAddress", "shouldnt@get.this");
        myPasswordHash = preferences.getString("myPasswordHash", "shouldntgetthis");
        myEmailWasVerified = preferences.getBoolean("myEmailWasVerified", false);
        
        sqliteAdapter = new SQLiteAdapter(this);
        
        sqliteAdapter.open();
        courseCodeCache = sqliteAdapter.getListOfCourseCodes();
        roomNumberCache = sqliteAdapter.getListOfRoomNumbers();
        sqliteAdapter.close();
        
        currentTimetablePersonId = getIntent().getIntExtra("userId", -2);
        if (currentTimetablePersonId == -2)
        {
            Log.e(TAG, "Don't know what the hell just happened");
        }
        else if (currentTimetablePersonId == -3) // Preview requested
        {
            currentTimetable = (ArrayList<ArrayList<Period>>)getIntent().getSerializableExtra("previewTimetable");
            
            // I won't add a check for this -3 case everywhere in this activity, instead
            // I'll hide the two buttons (edit/delete) that would use this ID for anything.
            // (Also currently there are no buttons in landscape mode)
            if (amInPortrait())
                showPreviewButtons();
        }
        else // Normal show timetable
        {
            currentTimetable = Period.makeEmptyTimetable();
            sqliteAdapter.open();
            sqliteAdapter.getPersonTimetable(currentTimetablePersonId, currentTimetable);
            sqliteAdapter.close();
        }
        personName = getIntent().getStringExtra("personName");
        if (personName == null)
            personName = "Ergh?";
        
        if (amInPortrait())
        {
            buildDays(false);
        }
        else // landscape
        {
            buildWeek();
        }
    }
    
    /**
     * Show the popup menu with available actions. 
     */
    public void showTimetableActions(View v)
    {
        PopupMenu popupMenu = new PopupMenu(this, v);
        popupMenu.getMenuInflater().inflate(R.menu.timetable_actions, popupMenu.getMenu());
        popupMenu.setOnMenuItemClickListener(this);
        popupMenu.show();
    }
    
    public boolean onMenuItemClick(MenuItem item)
    {
        int itemId = item.getItemId();
        
        if (itemId == R.id.action_delete_from_phone)
        {
            deleteCurrentTimetable();
            return true;
        }
        else if (itemId == R.id.action_change_history)
        {
            sqliteAdapter.open();
            int currentTimetablePersonSvrId = sqliteAdapter.getPersonSvrId(currentTimetablePersonId);
            sqliteAdapter.close();
            
            Intent myIntent = new Intent(this, ShowHistoryActivity.class);
            myIntent.putExtra("userId", currentTimetablePersonSvrId);
            startActivity(myIntent);
        }
        return false;
    }
    
    /**
     * You can delete any timetable in your list, but cannot delete your own because
     * that would make my life too complicated.
     */
    public void deleteCurrentTimetable()
    {
        // But not if it's your own
        if (currentTimetablePersonId == myPersonLiteId)
        {
            Toast.makeText(this, 
                           "You can't delete your own timetable. Use preferences to log out if that's what you want.", 
                           Toast.LENGTH_LONG).show();
            return;
        }
        
        AlertDialog.Builder builder = new AlertDialog.Builder(ShowTimetableActivity.this);
        builder.setMessage("Are you sure you want to delete this timetable from your list?");
        builder.setPositiveButton("Delete", new DialogInterface.OnClickListener() 
        {
            public void onClick(DialogInterface dialog, int id) 
            {
                sqliteAdapter.open();
                sqliteAdapter.deletePerson(currentTimetablePersonId);
                sqliteAdapter.close();
                finish();
            }
        });
        builder.setNegativeButton("Leave alone", new DialogInterface.OnClickListener() 
        { public void onClick(DialogInterface dialog, int id){ } });
        builder.create();
        builder.show();
    }
    
    /**
     *  Show the buttons for edit mode, hide others.
     */
    public void showEditButtons()
    {
        Button viewTimetableEditBtn = (Button)findViewById(R.id.viewTimetableEditBtn);
        viewTimetableEditBtn.setVisibility(View.GONE);
        Button viewTimetableMenuBtn = (Button)findViewById(R.id.viewTimetableMenuBtn);
        viewTimetableMenuBtn.setVisibility(View.GONE);
        Button viewTimetableSaveBtn = (Button)findViewById(R.id.viewTimetableSaveBtn);
        viewTimetableSaveBtn.setText("Save changes");
        viewTimetableSaveBtn.setEnabled(true);
        viewTimetableSaveBtn.setVisibility(View.VISIBLE);
        Button viewTimetableCancelBtn = (Button)findViewById(R.id.viewTimetableCancelBtn);
        viewTimetableCancelBtn.setVisibility(View.VISIBLE);
    }
    
    /**
     * Show buttons for view mode, hide others.
     */
    public void showViewButtons()
    {
        Button viewTimetableEditBtn = (Button)findViewById(R.id.viewTimetableEditBtn);
        viewTimetableEditBtn.setText("Edit");
        viewTimetableEditBtn.setEnabled(true);
        viewTimetableEditBtn.setVisibility(View.VISIBLE);
        Button viewTimetableMenuBtn = (Button)findViewById(R.id.viewTimetableMenuBtn);
        viewTimetableMenuBtn.setVisibility(View.VISIBLE);
        Button viewTimetableSaveBtn = (Button)findViewById(R.id.viewTimetableSaveBtn);
        viewTimetableSaveBtn.setVisibility(View.GONE);
        Button viewTimetableCancelBtn = (Button)findViewById(R.id.viewTimetableCancelBtn);
        viewTimetableCancelBtn.setVisibility(View.GONE);
    }

    /**
     * Show buttons for preview mode, hide others.
     */
    public void showPreviewButtons()
    {
        Button viewTimetableEditBtn = (Button)findViewById(R.id.viewTimetableEditBtn);
        viewTimetableEditBtn.setVisibility(View.GONE);
        Button viewTimetableMenuBtn = (Button)findViewById(R.id.viewTimetableMenuBtn);
        viewTimetableMenuBtn.setVisibility(View.GONE);
        Button viewTimetableSaveBtn = (Button)findViewById(R.id.viewTimetableSaveBtn);
        viewTimetableSaveBtn.setVisibility(View.GONE);
        Button viewTimetableCancelBtn = (Button)findViewById(R.id.viewTimetableCancelBtn);
        viewTimetableCancelBtn.setVisibility(View.GONE);
        Button viewTimetableYesBtn = (Button)findViewById(R.id.viewTimetableYesBtn);
        viewTimetableYesBtn.setVisibility(View.VISIBLE);
        Button viewTimetableNoBtn = (Button)findViewById(R.id.viewTimetableNoBtn);
        viewTimetableNoBtn.setVisibility(View.VISIBLE);
    }
    
    /**
     * Hide and show some buttons before calling makeEditable(). And also make
     * sure the user's email is verified.
     */
    public void editBtnCbk(View v)
    {
        if (myPersonLiteId == -1)
        {
            String errorText = "You have to log in before you can do this!";
            Toast.makeText(this, errorText, Toast.LENGTH_LONG).show();
            Log.i(TAG, errorText);
        }
        else if (myEmailWasVerified)
        {
            showEditButtons();
            makeEditable();
        }
        else
        {
            // Start a thread to check if the user's email is verified. The message in the button
            // doesn't say that, it would seem a little big-brotherish I feel.
            
            // Disable buttons
            Button viewTimetableEditBtn = (Button)findViewById(R.id.viewTimetableEditBtn);
            viewTimetableEditBtn.setText("Please wait...");
            viewTimetableEditBtn.setEnabled(false);
            Button viewTimetableMenuBtn = (Button)findViewById(R.id.viewTimetableMenuBtn);
            viewTimetableMenuBtn.setVisibility(View.GONE);
            
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
        // Reset the edit button to normal, in case of errors below.
        showViewButtons();
        
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
            
            // Do what editBtnCbk() was going to do in the first place
            showEditButtons();
            makeEditable();
        }
    }
    
    /**
     * This only works in portrait mode for now.
     * Go through the entire table and make all buttons in each period visible and clickable.
     */
    public void makeEditable()
    {
        if (!amInPortrait())
        {
            Toast.makeText(this, 
                           "Turn your phone upright to edit a timetable", 
                           Toast.LENGTH_LONG).show();
            
            // This actually worked but I decided editing in landscape will be a pain
            // for all who don't have a hardware keyboard.
            // Might restore this code later.
            /*TableLayout table = (TableLayout)findViewById(R.id.weekTable);
            // For every period (skip the first table row which has the weekday names)
            for (int period = 1; period < periods.length + 1; period++)
            {
                TableRow row = (TableRow)table.getChildAt(period);
                // For every day (skip the first column which has the time ranges)
                for (int day = 1; day < 6; day++)
                {
                    // Make existing full and empty periods visible and editable
                    Button button = (Button)row.getChildAt(day);
                    button.setVisibility(Button.VISIBLE);
                    button.setEnabled(true);
                }
            }*/
            
            return;
        }
        
        // For every day
        for (int day = 0; day < 5; day++)
        {
            ScrollView scrollView = (ScrollView)tableFlipper.getChildAt(day);
            TableLayout table = (TableLayout)scrollView.getChildAt(0);
            
            // For every period
            for (int period = 0; period < MainActivity.mySchool.timePeriods.length / 4; period++)
            {
                TableRow row = (TableRow)table.getChildAt(period);
                
                // Make existing full and empty periods visible and editable
                Button button = (Button)row.getChildAt(1);
                button.setVisibility(Button.VISIBLE);
                button.setEnabled(true);
                button.setOnClickListener(editPeriodBtnListener);
            }
        }
        amInEditMode = true;
    }
    
    /**
     * Attempt to save the changes on the server first and then in sqlite.
     */
    public void saveBtnCbk(View v)
    {
        makeUneditable();
        
        // Disable buttons
        Button viewTimetableSaveBtn = (Button)findViewById(R.id.viewTimetableSaveBtn);
        viewTimetableSaveBtn.setText("Saving...");
        viewTimetableSaveBtn.setEnabled(false);
        Button viewTimetableCancelBtn = (Button)findViewById(R.id.viewTimetableCancelBtn);
        viewTimetableCancelBtn.setVisibility(View.GONE);
        
        new UploadCurrentTimetable().execute();
    }

    /**
     * In preview mode the user picked YES to this timetable.
     */
    public void yesCbk(View v)
    {
        setResult(PREVIEW_RESULT_YES);
        finish();
    }

    /**
     * In preview mode the user picked NO to this timetable.
     */
    public void noCbk(View v)
    {
        finish();
    }
    
    private class UploadCurrentTimetable extends AsyncTask<Void, Void, Integer> 
    {
        String errorText = null;
        
        @SuppressWarnings("deprecation")
        public Integer doInBackground(Void... params) 
        {
            sqliteAdapter.open();
            int svrPersonId = sqliteAdapter.getPersonSvrId(currentTimetablePersonId);
            sqliteAdapter.close();
            
            // Create JSON with new timetable (also includes personId)
            JSONArray jsonPeriods = Period.makeJsonFromTimetable(currentTimetable);
            JSONObject jsonRequestParam = new JSONObject();
            try 
            {
                jsonRequestParam.put("personId", svrPersonId);
                jsonRequestParam.put("periods", jsonPeriods);
                
            } 
            catch (JSONException e) 
            {
                e.printStackTrace();
            }
            
            String command = "createNewTimetable" +
                             "&requestedByEmail=" + URLEncoder.encode(myEmailAddress) +
                             "&requestedByPassword=" + myPasswordHash +
                             "&timetable=" + URLEncoder.encode(jsonRequestParam.toString());
            
            String result;
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONObject contents = json.getJSONObject("contents");
                    int svrTimetableId = contents.getInt("timetableId");
                    return svrTimetableId;
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
            
            return -1;
        }
        
        public void onPostExecute(Integer newTimetableId) 
        {
            if (errorText != null)
            {
                Log.e(TAG, errorText);
                Toast.makeText(ShowTimetableActivity.this, errorText, Toast.LENGTH_LONG).show();
                return;
            }
            
            // Now that the new timetable is saved on the server - save it in sqlite as well
            sqliteAdapter.open();
            int updated = sqliteAdapter.saveTimetable(currentTimetablePersonId, newTimetableId, currentTimetable);
            sqliteAdapter.close();
            
            if (updated == 0)
                Toast.makeText(ShowTimetableActivity.this, "There was a problem saving the changes :(", Toast.LENGTH_LONG).show();
            else if (updated == 1)
                Toast.makeText(ShowTimetableActivity.this, "Changes saved", Toast.LENGTH_LONG).show();
            
            showViewButtons();
        }
    }
    
    /**
     * Cancel editing. While editing and deleting periods the actual contents of the data 
     * structures change, so I have to reload them from cache.
     */
    public void cancelBtnCbk(View v)
    {
        currentTimetable = Period.makeEmptyTimetable();
        sqliteAdapter.open();
        sqliteAdapter.getPersonTimetable(currentTimetablePersonId, currentTimetable);
        sqliteAdapter.close();
        buildDays(true);
        makeUneditable();
        showViewButtons();
    }
    
    /**
     * Reverse what makeEditable() did.
     */
    public void makeUneditable()
    {
        if (!amInPortrait())
        {
            Toast.makeText(this, "Err.. this should never happen", 
                           Toast.LENGTH_LONG).show();
            return;
        }
        
        // For every day
        for (int day = 0; day < 5; day++)
        {
            ScrollView scrollView = (ScrollView)tableFlipper.getChildAt(day);
            TableLayout table = (TableLayout)scrollView.getChildAt(0);
            
            // For every period
            for (int periodNum = 0; periodNum < MainActivity.mySchool.timePeriods.length / 4; periodNum++)
            {
                TableRow row = (TableRow)table.getChildAt(periodNum);
                
                Button button = (Button)row.getChildAt(1);
                Period period = (Period)button.getTag();
                
                // Nothing stays editable
                button.setEnabled(false);
                 
                // Empty periods are also hidden
                if (!period.isFull())
                    button.setVisibility(Button.INVISIBLE);
            }
        }
        amInEditMode = false;
    }
    
    /**
     * Callback for clicking a button to edit a period.
     */
    Button.OnClickListener editPeriodBtnListener = new Button.OnClickListener() 
    {
        @SuppressLint("InflateParams")
        @Override
        public void onClick(final View v) 
        {
            // Show dialog to edit this period
            
            final Period period = (Period)((Button)v).getTag();
            
            AlertDialog.Builder builder = new AlertDialog.Builder(ShowTimetableActivity.this);
            LayoutInflater inflater = ShowTimetableActivity.this.getLayoutInflater();
            
            builder.setTitle("Edit Period");
            
            // Create my own custom dialog view
            final View editView = inflater.inflate(R.layout.edit_period, null);
            
            // And fill it with data from this period
            final AutoCompleteTextView editCourseCode = (AutoCompleteTextView)(editView.findViewById(R.id.editCourseCode));
            editCourseCode.setText(period.courseCode);
            // And set up autocomplete for this field
            ArrayAdapter<String> codeAdapter = new ArrayAdapter<String>(ShowTimetableActivity.this,
                                                                    R.layout.simple_dropdown, courseCodeCache);
            editCourseCode.setAdapter(codeAdapter);
            editCourseCode.setThreshold(1);
            
            final AutoCompleteTextView editRoomNumber = (AutoCompleteTextView)(editView.findViewById(R.id.editRoomNumber));
            editRoomNumber.setText(period.room);
            // And set up autocomplete for this field
            ArrayAdapter<String> roomAdapter = new ArrayAdapter<String>(ShowTimetableActivity.this,
                                                                    R.layout.simple_dropdown, roomNumberCache);
            editRoomNumber.setAdapter(roomAdapter);
            editRoomNumber.setThreshold(1);
            
            // Inspiration: http://developer.android.com/guide/topics/ui/dialogs.html
            builder.setView(editView);
            // When OK is pressed
            builder.setPositiveButton("OK", new DialogInterface.OnClickListener() 
            {
                @Override
                public void onClick(DialogInterface dialog, int id) 
                {
                    String editedCourseCode = editCourseCode.getText().toString();
                    String editedRoomNumber = editRoomNumber.getText().toString();
                    
                    // Update the data stored in the button
                    period.courseCode = editedCourseCode;
                    period.room = editedRoomNumber;
                    
                    // And the button's view too
                    Button b = (Button)v;
                    b.setText(period.courseCode + " - " + period.room);
                    
                    if (!courseCodeCache.contains(editedCourseCode))
                        courseCodeCache.add(0, editedCourseCode);
                    if (!roomNumberCache.contains(editedRoomNumber))
                        roomNumberCache.add(0, editedRoomNumber);
                }
            });
            // When Delete is pressed
            builder.setNeutralButton("Delete", new DialogInterface.OnClickListener() 
            {
                @Override
                public void onClick(DialogInterface dialog, int id) 
                {
                    period.courseCode = "";
                    period.room = "";
                    Button b = (Button)v;
                    b.setText("edit");
                }
            });
            // When Cancel is pressed
            builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() 
            { public void onClick(DialogInterface dialog, int id) {} });
            builder.create();
            builder.show();
        } // onClick()
    }; // editPeriodBtnListener
    
    /**
     * Only one special case handled currently: 
     * When in edit mode cancel the updates to the timetable and make in uneditable again. 
     */
    @Override
    public void onBackPressed() 
    {
        if (amInEditMode)
        {
            cancelBtnCbk(null);
        }
        else
            super.onBackPressed();
    };
    
    /**
     * Am I in portrait mode?
     */
    public boolean amInPortrait()
    {
        int currentOrientation = getScreenOrientation(); 
        if (currentOrientation == ActivityInfo.SCREEN_ORIENTATION_PORTRAIT ||
            currentOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT)
        {
            return true;
        }
        else
            return false;
    }
    
    /**
     *  ViewFlipper example referenced: http://learnandroideasily.blogspot.ca/2013/06/android-viewflipper-example.html
     */
    public boolean onTouchEvent(MotionEvent touchevent) 
    {
        if (amInPortrait())
        {
            if (touchevent.getAction() == MotionEvent.ACTION_DOWN)
                lastXtouched = touchevent.getX();
            else if (touchevent.getAction() == MotionEvent.ACTION_UP) 
            {
                float currentX = touchevent.getX();
                if (lastXtouched < currentX - minSwipeLen) 
                {
                    showPreviousDay();
                }
                else if (lastXtouched > currentX + minSwipeLen) 
                {
                    showNextDay();
                }
            }
        }
        
        return true;
    }
    
    /**
     * Callback on the button.
     */
    public void showPreviousDayCbk(View v) 
    {
        showPreviousDay();
    }
    
    /**
     * Flip both the title and timetable view flippers to the previous day if it exists.
     */
    public void showPreviousDay()
    {
        if (titleFlipper.getDisplayedChild() > 0 ) 
        {    
            titleFlipper.setInAnimation(this, R.anim.in_from_left);
            titleFlipper.setOutAnimation(this, R.anim.out_to_right);
            titleFlipper.showPrevious();
            
            tableFlipper.setInAnimation(this, R.anim.in_from_left);
            tableFlipper.setOutAnimation(this, R.anim.out_to_right);
            tableFlipper.showPrevious();
            
            Button nextDayBtn = (Button)findViewById(R.id.rightBtn);
            nextDayBtn.setEnabled(true);
        }
        Button prevDayBtn = (Button)findViewById(R.id.leftBtn);
        if (titleFlipper.getDisplayedChild() == 0)
            prevDayBtn.setEnabled(false);
        else
            prevDayBtn.setEnabled(true);
    }
    
    /**
     * Callback on the button.
     */
    public void showNextDayCbk(View v) 
    {
        showNextDay();
    }

    /**
     * Flip both the title and timetable view flippers to the next day if it exists.
     */
    public void showNextDay() 
    {
        if (titleFlipper.getDisplayedChild() < 4 ) 
        {    
            titleFlipper.setInAnimation(this, R.anim.in_from_right);
            titleFlipper.setOutAnimation(this, R.anim.out_to_left);
            titleFlipper.showNext();

            tableFlipper.setInAnimation(this, R.anim.in_from_right);
            tableFlipper.setOutAnimation(this, R.anim.out_to_left);
            tableFlipper.showNext();
            
            Button prevDayBtn = (Button)findViewById(R.id.leftBtn);
            prevDayBtn.setEnabled(true);
        }
        Button nextDayBtn = (Button)findViewById(R.id.rightBtn);
        if (titleFlipper.getDisplayedChild() == 4)
            nextDayBtn.setEnabled(false);
        else
            nextDayBtn.setEnabled(true);
    }
    
    /**
     * Construct the string that goes at the top of the app.
     * E.g. "Andrew Smith - Entire week"
     */
    public String makeTitleString(String name, int dayNum)
    {
        String title = "";
        
        if (currentTimetablePersonId == -3)
            title += "PREVIEW\nIs this the one?\n\n";
        
        title += name;
        
        if (amInPortrait())
        {
            title += '\n';
        }
        else
        {
            title += " - ";
        }
        
        if (dayNum == -1)
            title += "Entire week";
        else if (dayNum == 0)
            title += "Monday";
        else if (dayNum == 1)
            title += "Tuesday";
        else if (dayNum == 2)
            title += "Wednesday";
        else if (dayNum == 3)
            title += "Thursday";
        else if (dayNum == 4)
            title += "Friday";
        
        return title;
    }
    
    /**
     * Build the contents of the view flipper used for daily views.
     * If rebuild is true - will remove existing rows before creating new ones.
     */
    public void buildDays(Boolean rebuild) 
    {
        titleFlipper = (ViewFlipper)findViewById(R.id.titleFlipper);
        tableFlipper = (ViewFlipper)findViewById(R.id.tableFlipper);
        
        // For each day
        for (int dayNum = 0; dayNum < 5; dayNum++) 
        {
            ArrayList<Period> todayPeriods = currentTimetable.get(dayNum);
            
            TextView title = (TextView)titleFlipper.getChildAt(dayNum);
            String titleStr = makeTitleString(personName, dayNum);
            title.setText(titleStr);
            
            // Dynamic table creation example referenced: http://stackoverflow.com/questions/7279501/programatically-adding-tablerow-to-tablelayout-not-working
            
            /* Find Tablelayout for this day */
            ScrollView scrollView = (ScrollView)tableFlipper.getChildAt(dayNum);
            TableLayout tl = (TableLayout)scrollView.getChildAt(0);
            if (rebuild)
            {
                tl.removeAllViews();
            }
            
            // For each period
            for (int periodNum = 0; periodNum < MainActivity.mySchool.timePeriods.length / 4; periodNum++) 
            {
                /* Create row */
                TableRow tr = new TableRow(this);
                tr.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
                tl.addView(tr, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
                
                /* First cell - time range */
                TextView time = new TextView(this);
                time.setText(timePeriodString(periodNum));
                time.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
                time.setGravity(Gravity.CENTER);
                tr.addView(time);
                
                /* Second cell - full with a period or empty period */
                Period period = todayPeriods.get(periodNum);
                Button periodBtn = new Button(this);
                periodBtn.setTag(period);
                if (period.isFull())
                {
                    periodBtn.setText(period.courseCode + " - " + period.room);
                }
                else
                {
                    periodBtn.setText("edit");
                    periodBtn.setVisibility(Button.INVISIBLE);
                }
                periodBtn.setEnabled(false);
                periodBtn.setTextColor(Color.BLACK);
                periodBtn.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
                tr.addView(periodBtn);
            } // for each possible period
        } // for each day
    }

    /**
     * Build the layout for the entire week view and fill it with contents.
     */
    public void buildWeek() 
    {
        TextView title = (TextView)findViewById(R.id.landscapeTitle);
        String titleStr = makeTitleString(personName, -1);
        title.setText(titleStr);
        
        TableLayout table = (TableLayout)findViewById(R.id.weekTable);
        
        // Build the table one row (one period) at a time.
        for (int periodNum = 0; periodNum < MainActivity.mySchool.timePeriods.length / 4; periodNum++) 
        {
            // Create row 
            TableRow row = new TableRow(this);
            row.setLayoutParams(new TableRow.LayoutParams(TableRow.LayoutParams.MATCH_PARENT, TableRow.LayoutParams.WRAP_CONTENT));
            row.setGravity(Gravity.CENTER_VERTICAL);
            table.addView(row, new TableLayout.LayoutParams(TableLayout.LayoutParams.MATCH_PARENT, TableLayout.LayoutParams.WRAP_CONTENT));
            
            // First cell - time for the period 
            TextView time = new TextView(this);
            time.setText(timePeriodString(periodNum));
            time.setGravity(Gravity.CENTER);
            row.addView(time);
            
            // Second to sixth cells - either a full or empty time period
            for (int dayNum = 0; dayNum < 5; dayNum++)
            {
                Period period = currentTimetable.get(dayNum).get(periodNum);
                
                Button periodBtn = new Button(this);
                periodBtn.setTag(period);
                periodBtn.setPadding(3, 3, 3, 3);
                periodBtn.setWidth(0);
                periodBtn.setEnabled(false);
                periodBtn.setTextColor(Color.BLACK);
                
                // If have anything for this day for this time slot
                if (period.isFull())
                {
                    // Then put it into the row
                    periodBtn.setText(period.courseCode + "\n" + period.room);
                }
                else // empty period for this day
                {
                    periodBtn.setText("edit");
                    periodBtn.setVisibility(Button.INVISIBLE);
                }
                
                row.addView(periodBtn);
            } // for each column in the row
        } // for each row
    }
    
    /**
     * Returns one of:
     *  ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
     *  ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
     *  ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
     *  ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
     *  
     *  Source: http://stackoverflow.com/questions/10380989/how-do-i-get-the-current-orientation-activityinfo-screen-orientation-of-an-a/10381580
     */
    public int getScreenOrientation()
    {
        int rotation = getWindowManager().getDefaultDisplay().getRotation();
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        int orientation;
        // if the device's natural orientation is portrait:
        if ((rotation == Surface.ROTATION_0
                || rotation == Surface.ROTATION_180) && height > width ||
            (rotation == Surface.ROTATION_90
                || rotation == Surface.ROTATION_270) && width > height) 
        {
            switch(rotation) 
            {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to " +
                            "portrait.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;              
            }
        }
        // if the device's natural orientation is landscape or if the device
        // is square:
        else
        {
            switch(rotation)
            {
                case Surface.ROTATION_0:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;
                case Surface.ROTATION_90:
                    orientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
                    break;
                case Surface.ROTATION_180:
                    orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
                    break;
                case Surface.ROTATION_270:
                    orientation =
                        ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
                    break;
                default:
                    Log.e(TAG, "Unknown screen orientation. Defaulting to " +
                            "landscape.");
                    orientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
                    break;              
            }
        }

        return orientation;
    }
    
    /**
     * Make a string representation of this period.
     */
    public String timePeriodString(int periodNum)
    {
        return String.format("%02d:%02d - %02d:%02d", MainActivity.mySchool.timePeriods[periodNum*4],
                                                      MainActivity.mySchool.timePeriods[periodNum*4+1],
                                                      MainActivity.mySchool.timePeriods[periodNum*4+2],
                                                      MainActivity.mySchool.timePeriods[periodNum*4+3]);
    }
}
