package com.insertcoolnamehere.mymoney;

import android.app.Service;
import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class UpdateBalanceService extends JobService {

    private static final String LOG_TAG = UpdateBalanceService.class.getName();
    public static final String EXTRA_ACCOUNT = "ACCOUNT_NUMBER";
    public static final String EXTRA_BALANCE = "CURRENT_BALANCE";

    private UserLoginTask mAuthTask = null;
    private JobParameters mParams;

    public UpdateBalanceService() {
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        mParams = params;
        String accountNo = mParams.getExtras().getString(EXTRA_ACCOUNT);
        if (mAuthTask == null)
            mAuthTask = new UserLoginTask(accountNo);
        mAuthTask.execute();

        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        mAuthTask.cancel(true);
        return true;
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean> {

        private final String mAccountNo;
        private double mBalance;

        UserLoginTask(String accountNo) {
            mAccountNo = accountNo;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            String key = "fdaf93858506a13dff10d8a526650bce";

            try {
                URL getBalanceURL = new URL("http://api.reimaginebanking.com/accounts/"+mAccountNo+"?key="+key);
                HttpURLConnection conn = (HttpURLConnection) getBalanceURL.openConnection();
                conn.setRequestMethod("GET");
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(15000);
                conn.connect();

                Log.d(LOG_TAG, "status code: "+conn.getResponseCode());

                BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line = "";
                String responseBody = "";
                while((line = reader.readLine()) != null) {
                    responseBody += line;
                }

                JSONObject account = new JSONObject(responseBody);
                mBalance = account.getDouble("balance");
                return true;
            } catch (MalformedURLException e) {
                Log.e(LOG_TAG, "You suck at copy-paste");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Couldn't open connection");
                e.printStackTrace();
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Couldn't parse JSON");
                e.printStackTrace();
            }

            return false;
        }

        @Override
        protected void onPostExecute(final Boolean success) {
            if (success) {
                // update balance in widget
                Context cxt = UpdateBalanceService.this;
                AppWidgetManager manager = AppWidgetManager.getInstance(cxt);
                ComponentName componentName = new ComponentName(cxt, BalanceWidget.class);
                int[] ids = manager.getAppWidgetIds(componentName);
                for(int id: ids)
                    BalanceWidget.updateAppWidget(cxt, manager, id, mBalance);

                Log.d(LOG_TAG, "Auth Task success!");
                jobFinished(mParams, false);
            } else {
                Log.d(LOG_TAG, "Auth Task failed, requested reschedule");
                jobFinished(mParams, true);
            }
            mAuthTask = null;
        }
    }
}
