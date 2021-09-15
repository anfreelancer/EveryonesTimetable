package com.luteapp.everyonestimetable;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

public class ShowHistoryActivity extends Activity
{
    static final String TAG = "EveryonesTimetable";
    BlamePeopleAdapter blamePeopleAdapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_history);
        Log.d(TAG, "onCreate");
        // This is the list for results
        ListView listView = (ListView)findViewById(R.id.showHistoryLV);
        // This adapter is used to connect the list of results found with
        // a the listview above and also a custom view for each row.
        blamePeopleAdapter = new BlamePeopleAdapter(this, R.layout.show_history_row);
        listView.setAdapter(blamePeopleAdapter);
        
        int historyForUserId = getIntent().getIntExtra("userId", -1);
        new HistoryRetriever().execute(historyForUserId);
    }
    
    /**
     * Data for one line in the log of who edited the timetable.
     */
    class Blame
    {
        String fullName;
        String email;
        String date;
        
        public Blame(String fullName, String emailAddress, String date)
        {
            this.fullName = fullName;
            this.email= emailAddress;
            this.date = date;
        }
    }

    /**
     * This only exists because I wanted to override getView() so that each row in the list
     * would show more than one string (see find_timetable_row.xml).
     * A similar class is in the register code in MainActivity.
     */
    private class BlamePeopleAdapter extends ArrayAdapter<Blame>
    {
        Context context;
        int layoutResourceId;
        
        public BlamePeopleAdapter(Context context, int layoutResourceId)
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
                ImageButton previewBtn = (ImageButton)row.findViewById(R.id.showHistoryRowPreviewBtn);
                previewBtn.setOnClickListener(new View.OnClickListener()
                {
                    // When preview button is clicked - download that version of the
                    // timetable in a separate thread, to later show in a ShowTimetableActivity
                    // with no action buttons.
                    @Override
                    public void onClick(View button) 
                    {
                        
                    }
                });
            }
            
            TextView name = (TextView)row.findViewById(R.id.largerTextAbove);
            TextView description = (TextView)row.findViewById(R.id.smallerTextBelow);
            
            Blame rowData = getItem(position);
            row.setTag(rowData);
            
            name.setText(rowData.date + "\n" + rowData.fullName);
//            description.setText(rowData.email);
            
            return row;
        }
    };

    /**
     * The AsyncTask I use to get the changelog online.
     * The results are passed back via blamePeopleAdapter
     */
    private class HistoryRetriever extends AsyncTask<Integer, Void, ArrayList<Blame>> 
    {
        String errorText = null;
        
        @Override
        protected ArrayList<Blame> doInBackground(Integer... params) 
        {
            ArrayList<Blame> resultList = new ArrayList<Blame>();
            
            String command = "getTimetableHistory" + "&personId=" + params[0];
            
            String result;
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONArray blameArray = json.getJSONArray("contents");
                    for (int i = 0; i < blameArray.length(); i++)
                    {
                        JSONObject blameJson = blameArray.getJSONObject(i);
                        
                        String fullName = blameJson.getString("fullName");
                        String email = blameJson.getString("email");
                        String date = blameJson.getString("date");
                        
                        resultList.add(new Blame(fullName, email, date));
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
        protected void onPostExecute(ArrayList<Blame> result) 
        {
            if (errorText != null)
            {
                Toast.makeText(ShowHistoryActivity.this, errorText, Toast.LENGTH_LONG).show();
                Log.e(TAG, errorText);
                
                // The rest of the function can (no, /should/) continue even in case of error.
            }
            
            for (Blame person : result)
                blamePeopleAdapter.add(person);

            // Hide progress spinner
            ProgressBar spinner = (ProgressBar)findViewById(R.id.showHistorySpinner);
            spinner.setVisibility(View.GONE);
            View view = findViewById(R.id.showHistorySpacerView);
            view.setVisibility(View.GONE);
            // Show results list
            ListView listView = (ListView)findViewById(R.id.showHistoryLV);
            listView.setVisibility(View.VISIBLE);
        }
    }
}
