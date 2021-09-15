package com.luteapp.everyonestimetable;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

public class Globals
{
    public static final String TAG = "EveryonesTimetable";
    public static final String wsURL = MainActivity.context.getString(R.string.et_php_url);
    // The index in this array is the number stored in the "type" column for Person
    public static final String[] personType = {"Unknown", "Student", "Professor"};
    
    /**
     * This only exists so that the CheckIsUserVerified below can call back to it's 
     * caller and report a result.
     */
    public interface CheckIsUserVerifiedCallback
    {
        /**
         * @param result can be -1 (error), 0 (not verified), or 1 (verified).
         * @param errorText has an error if an error happened.
         */
        void checkIsUserVerifiedCompleted(int result, String errorText);
    };
    
    /**
     * This calls checkCredentialsAndGetUserInfo but all it wants out of the returned UserInfo
     * is emailVerified.
     */
    public static class CheckIsUserVerified extends AsyncTask<String, Void, Integer> 
    {
        CheckIsUserVerifiedCallback completedCallback;
        String errorText = null;
        String myEmailAddress;
        String myPasswordHash;
        
        public CheckIsUserVerified(CheckIsUserVerifiedCallback completedCallback) 
        {
            this.completedCallback = completedCallback;            
        }
        
        // credentials[0] is email
        // credentials[1] is password
        @SuppressWarnings("deprecation")
        public Integer doInBackground(String... credentials) 
        {
            myEmailAddress = credentials[0];
            myPasswordHash = credentials[1];
            
            String command = "checkCredentialsAndGetUserInfo" +
                    "&email=" + URLEncoder.encode(myEmailAddress) +
                    "&passwordHash=" + myPasswordHash;
            
            String result = null;
            try
            {
                result = Globals.queryServer(command);
                
                JSONObject json = new JSONObject(result);
                boolean querySucceeded = json.getBoolean("success");
                if (querySucceeded)
                {
                    JSONObject contents = json.getJSONObject("contents");
                    Boolean emailVerified = contents.getBoolean("emailVerified");
                    if (emailVerified)
                        return 1;
                    else
                        return 0;
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
        
        // Either show an error or continue with the use case flow.
        public void onPostExecute(Integer result) 
        {
            completedCallback.checkIsUserVerifiedCompleted(result, errorText);
        }
    }
    
    public static String formatServerErrorStack(JSONObject json) 
           throws JSONException
    {
        String result = "Server error: ";
        JSONArray errorStack = json.getJSONArray("errorStack");
        for (int i = errorStack.length() - 1; i >= 0; i--)
        {
            String error = errorStack.getString(i);
            result += error;
            if (i > 0)
                result += ": ";
        }
        
        return result;
    }
    
    /**
     * Retrieve the entire timetable for this person. Used when getting a list of search
     * results when registering or finding a timetable.
     * !! I should limit how many times this gets called, and when I do that
     * it will make sense to have this in a separate call, to save on SQL queries.
     */
    public static ArrayList<ArrayList<Period>> getTimetableForPerson(int timetableId)
           throws EncryptionException, ServerCommException, JSONException
    {
        JSONObject json = new JSONObject();
        ArrayList<ArrayList<Period>> timetable;
        
        String command = "getTimetable" + "&timetableId=" + timetableId;
        String result = queryServer(command);
        
        json = new JSONObject(result);
        timetable = Period.makeTimetableFromJson(json.getJSONObject("contents").getJSONArray("timePeriods"));
        
        return timetable;
    }

    /**
     * Google keeps breaking my setUpHttpsConnection(), so fuck it, I'm just going to use plain text.
     * Happy now?
     * Assholes.
     */
    public static HttpURLConnection setUpHttpConnection(String urlString)
    {
        URL url = null;
        try
        {
            url = new URL(urlString);
        }
        catch (MalformedURLException e)
        {
            e.printStackTrace();
        }
        HttpURLConnection urlConnection = null;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return urlConnection;
    }

    /**
     * Set up a connection to littlesvr.ca using HTTPS. An entire function
     * is needed to do this because littlesvr.ca has a self-signed certificate.
     *
     * The caller of the function would do something like:
     * HttpsURLConnection urlConnection = setUpHttpsConnection("https://littlesvr.ca");
     * InputStream in = urlConnection.getInputStream();
     * And read from that "in" as usual in Java
     *
     * Based on code from:
     * https://developer.android.com/training/articles/security-ssl.html#SelfSigned
     */
    @SuppressLint("SdCardPath")
    public static HttpsURLConnection setUpHttpsConnection(String urlString) 
           throws EncryptionException
    {
        try
        {
            // Load CAs from an InputStream
            // (could be from a resource or ByteArrayInputStream or ...)
            CertificateFactory cf = CertificateFactory.getInstance("X.509");
            
            // My CRT file that I put in the assets folder
            // I got this file by following these steps:
            // * Go to https://littlesvr.ca using Firefox
            // * Click the padlock/More/Security/View Certificate/Details/Export
            // * Saved the file as littlesvr.crt (type X.509 Certificate (PEM))
            // The MainActivity.context is declared as:
            // public static Context context;
            // And initialized in MainActivity.onCreate() as:
            // MainActivity.context = this;
            InputStream caInput = new BufferedInputStream(MainActivity.context.getAssets().open("littlesvr.crt"));
            Certificate ca = cf.generateCertificate(caInput);
            System.out.println("ca=" + ((X509Certificate) ca).getSubjectDN());
            
            // Create a KeyStore containing our trusted CAs
            String keyStoreType = KeyStore.getDefaultType();
            KeyStore keyStore = KeyStore.getInstance(keyStoreType);
            keyStore.load(null, null);
            keyStore.setCertificateEntry("ca", ca);
            
            // Create a TrustManager that trusts the CAs in our KeyStore
            String tmfAlgorithm = TrustManagerFactory.getDefaultAlgorithm();
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(tmfAlgorithm);
            tmf.init(keyStore);
            
            // Create an SSLContext that uses our TrustManager
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, tmf.getTrustManagers(), null);
            
            // Tell the URLConnection to use a SocketFactory from our SSLContext
            URL url = new URL(urlString);
            HttpsURLConnection urlConnection = (HttpsURLConnection)url.openConnection();
            urlConnection.setSSLSocketFactory(context.getSocketFactory());
            
            return urlConnection;
        }
        catch (Exception ex)
        {
            throw new EncryptionException("Failed to establish SSL connection to server", ex);
        }
    }
    
    /**
     * Query the server using the command parameter and return the result.
     * Returns an empty string in case of an error.
     */
    public static String queryServer(String command) 
           throws EncryptionException, ServerCommException
    {
        BufferedReader reader;
        StringBuilder sb = new StringBuilder();
        String jsonString = "";
        
        Log.d(TAG, "curl -k -d 'command=" + command + "' '" + wsURL + "'");
        
        HttpURLConnection urlConnection = setUpHttpConnection(wsURL);
        urlConnection.setDoOutput(true);
        try {
            urlConnection.setRequestMethod("POST");
        } catch (ProtocolException e1) { }
        
        // Send the parameters via POST:
        DataOutputStream out;
        try
        {
            out = new DataOutputStream(urlConnection.getOutputStream());
            out.writeBytes("command=" + command);
            out.flush();
            out.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
            throw new ServerCommException("Couldn't communicate with the server, is a network available?", e);
        }
        
        // Read the entire response into jsonString
        try 
        {
            InputStream in = urlConnection.getInputStream();
            reader = new BufferedReader(new InputStreamReader(in, "UTF-8"), 8);
            sb = new StringBuilder();

            String line = null;
            while ((line = reader.readLine()) != null)
                sb.append(line + "\n");
            jsonString = sb.toString();
        } 
        catch (IOException e) 
        {
            e.printStackTrace();
            throw new ServerCommException("Couldn't communicate with the server, is a network available?", e);
        }
        
        Log.d(TAG, "queryServer() returns: '" + jsonString + "'");
        
        return jsonString;
    }
    
    /**
     * Used to hash passwords.
     */
    public static String makeSHA512Hash(String input)
    {
        MessageDigest md;
        try 
        {
            md = MessageDigest.getInstance("SHA512");
        } 
        catch (NoSuchAlgorithmException e) 
        {
            e.printStackTrace();
            return "NoSuchAlgorithException";
        }
        md.reset();
        byte[] buffer = input.getBytes();
        md.update(buffer);
        byte[] digest = md.digest();

        String hexStr = "";
        for (int i = 0; i < digest.length; i++) {
            hexStr +=  Integer.toString( ( digest[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return hexStr;
    }
    
    public static void showAlert(Context context, String message)
    {
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() 
                              {public void onClick(DialogInterface dialog, int which) {}});
        alertDialog.show();
    }
    
    /**
     * Used to show an alert (cause I don't think Android has one).
     */
    public static void showNotAllowed(Context context, String message) 
    {
        AlertDialog alertDialog = new AlertDialog.Builder(context).create();
        alertDialog.setTitle("Just a minute");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK", new DialogInterface.OnClickListener() 
                              {public void onClick(DialogInterface dialog, int which) {}});
        alertDialog.show();
    }
}
