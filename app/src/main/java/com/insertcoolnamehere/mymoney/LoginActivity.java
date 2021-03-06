package com.insertcoolnamehere.mymoney;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PersistableBundle;
import android.support.annotation.NonNull;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.app.LoaderManager.LoaderCallbacks;

import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;

import android.os.Build;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import static android.Manifest.permission.READ_CONTACTS;

/**
 * A login screen that offers login via email/password.
 */
public class LoginActivity extends AppCompatActivity {

    final String LOG_TAG = "LoginActivity";
    double balance = 0.0;

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;
    private JobScheduler mJobScheduler = null;
    private final int UPDATE_BALANCE_JOB = 1;

    // UI references.
    private EditText mAccountView;
    private TextView mBalanceView;
    private View mProgressView;
    private View mLoginFormView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);

        mAccountView = (EditText) findViewById(R.id.accountno);
        mAccountView.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent) {
                if (id == EditorInfo.IME_ACTION_DONE || id == EditorInfo.IME_NULL) {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        Button mSignInButton = (Button) findViewById(R.id.email_sign_in_button);
        mSignInButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {
                attemptLogin();
            }
        });

        mLoginFormView = findViewById(R.id.login_form);
        mProgressView = findViewById(R.id.login_progress);
        mBalanceView = findViewById(R.id.balance_view);

        mJobScheduler = (JobScheduler) getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    private void attemptLogin() {
        if (mAuthTask != null) {
            return;
        }

        // Reset errors.
        mAccountView.setError(null);

        // Store values at the time of the login attempt.
        String accountNo = mAccountView.getText().toString();

        // wipe account number
        mAccountView.setText("");

        boolean cancel = false;
        View focusView = null;

        // Check if the user entered account number
        if (TextUtils.isEmpty(accountNo)) {
            mAccountView.setError(getString(R.string.error_no_account));
            focusView = mAccountView;
            cancel = true;
        }

        if (cancel) {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        } else {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            showProgress(true);
            mAuthTask = new UserLoginTask(accountNo);
            mAuthTask.execute((Void) null);
        }
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show) {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2) {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(
                    show ? 0 : 1).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });

            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mProgressView.animate().setDuration(shortAnimTime).alpha(
                    show ? 1 : 0).setListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
                }
            });
        } else {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mProgressView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Integer> {

        private final String mAccountNo;
        private double mBalance;

        private final int SUCCESS = 0;
        private final int NO_INTERNET = 1;
        private final int INVALID_ACCOUNT = 2;

        UserLoginTask(String accountNo) {
            mAccountNo = accountNo;
        }

        @Override
        protected Integer doInBackground(Void... params) {
            String key = "fdaf93858506a13dff10d8a526650bce";

            try {
                ConnectivityManager cm =
                        (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);

                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null &&
                        activeNetwork.isConnectedOrConnecting();

                if (!isConnected)
                    return NO_INTERNET;

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
                return SUCCESS;
            } catch (MalformedURLException e) {
                Log.e(LOG_TAG, "You suck at copy-paste");
                e.printStackTrace();
            } catch (IOException e) {
                Log.e(LOG_TAG, "Couldn't open connection");
                e.printStackTrace();
            } catch (JSONException e) {
                Log.e(LOG_TAG, "Couldn't parse JSON");
                e.printStackTrace();
            } catch (NullPointerException e) {
                Log.e(LOG_TAG, "The internet conenctivity is broken");
                e.printStackTrace();
            }

            return INVALID_ACCOUNT;
        }

        @Override
        protected void onPostExecute(final Integer result) {
            mAuthTask = null;
            showProgress(false);

            if (result == SUCCESS) {
                mBalanceView.setText("Balance: $"+mBalance);

                // notify user
                Toast.makeText(LoginActivity.this, "Successfully fetched balance!", Toast.LENGTH_SHORT).show();

                // update balance in widget
                Context cxt = LoginActivity.this;
                AppWidgetManager manager = AppWidgetManager.getInstance(cxt);
                ComponentName componentName = new ComponentName(cxt, BalanceWidget.class);
                int[] ids = manager.getAppWidgetIds(componentName);
                for(int id: ids)
                    BalanceWidget.updateAppWidget(cxt, manager, id, mBalance);

                // schedule future updates
                JobInfo.Builder builder = new JobInfo.Builder(UPDATE_BALANCE_JOB,
                        new ComponentName("com.insertcoolnamehere.mymoney", UpdateBalanceService.class.getName()));
                builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
                builder.setPeriodic(60L * 1000L * 15L);
                PersistableBundle bundle = new PersistableBundle();
                bundle.putString(UpdateBalanceService.EXTRA_ACCOUNT, mAccountNo);
                builder.setExtras(bundle);
                mJobScheduler.schedule(builder.build());
            } else if (result == NO_INTERNET) {
                // notify user if there is no Internet
                Toast.makeText(LoginActivity.this, "No internet connection", Toast.LENGTH_SHORT).show();
            } else {
                mAccountView.setError(getString(R.string.error_invalid_account));
                mAccountView.requestFocus();
            }
        }

        @Override
        protected void onCancelled() {
            mAuthTask = null;
            showProgress(false);
        }
    }
}

