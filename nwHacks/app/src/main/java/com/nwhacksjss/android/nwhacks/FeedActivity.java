package com.nwhacksjss.android.nwhacks;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.Switch;

import com.google.gson.Gson;
import com.nwhacksjss.android.nwhacks.services.TweetUpdateService;
import com.nwhacksjss.android.nwhacks.utils.PermissionUtils;
import com.nwhacksjss.android.nwhacks.utils.PreferenceUtils;
import com.twitter.sdk.android.core.models.Tweet;
import com.twitter.sdk.android.tweetui.TweetView;

import java.util.ArrayList;
import java.util.List;

public class FeedActivity extends AppCompatActivity {

    private static final String TAG = FeedActivity.class.getSimpleName();

    // The receiver used to get tweet updates from LocationUpdatesService
    private TweetUpdateReceiver tweetUpdateReceiver;

    // A reference to the service used to get location updates.
    private TweetUpdateService tweetUpdateService = null;

    // Tracks the bound state of the service.
    private boolean isBoundToTweetUpdates = false;

    // Monitors the state of the connection to the service.
    private final ServiceConnection tweetUpdateServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            TweetUpdateService.LocalBinder binder = (TweetUpdateService.LocalBinder) service;
            tweetUpdateService = binder.getService();
            isBoundToTweetUpdates = true;
            Log.i(TAG, "Requesting tweet updates from TweetUpdateService.");
            tweetUpdateService.requestTweetUpdates();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            tweetUpdateService = null;
            isBoundToTweetUpdates = false;
        }
    };

    private Context context;

    // UI elements
    private View view;
    private View contentFeed;
    private LinearLayout linearLayout;
    private ProgressBar progressBar;
    private Switch trackMeSwitch;

    private static List<Tweet> tweets = new ArrayList<>();

    private static boolean firstRun = true; // TODO - this is for debugging...remove eventually

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_feed);
        context = this;

        view = findViewById(R.id.activity_feed);
        contentFeed = findViewById(R.id.content_feed);
        linearLayout = findViewById(R.id.feed_layout);
        progressBar = findViewById(R.id.progress_bar_content_feed);
        trackMeSwitch = findViewById(R.id.track_me_mode);

        trackMeSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // If track me button is checked, set track me foreground service to be active
                TweetUpdateService.setTrackMeMode(isChecked);
                PreferenceUtils.setPrefTrackMeMode(context, isChecked);
            }
        });

        PermissionUtils.fullRequestPermissionProcess(this, view);

        tweetUpdateReceiver = new FeedActivity.TweetUpdateReceiver();

        addMapButton();

        initContentFeed();
    }

    private void initContentFeed() {
        if (tweets.size() > 0) {
            repopulateFeed();
            showContentFeed();
        } else {
            showLoadingSpinner();
        }
    }

    private void showLoadingSpinner() {
        progressBar.setVisibility(View.VISIBLE);
        contentFeed.setVisibility(View.INVISIBLE);
    }

    private void showContentFeed() {
        progressBar.setVisibility(View.INVISIBLE);
        contentFeed.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (!PermissionUtils.isLocationPermissionGranted(this)) {
            PermissionUtils.requestPermission(this);
        } else {
            // Bind to the service
            Log.i(TAG, "Binding TweetUpdateService.");
            bindService(new Intent(this, TweetUpdateService.class), tweetUpdateServiceConnection,
                    Context.BIND_AUTO_CREATE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        setButtonsState(PreferenceUtils.getTrackMeModeStatus(context));

        Log.i(TAG, "Registering TweetUpdateReceiver.");
        LocalBroadcastManager.getInstance(this).registerReceiver(tweetUpdateReceiver,
                new IntentFilter(TweetUpdateService.ACTION_BROADCAST));
    }

    @Override
    protected void onPause() {
        Log.i(TAG, "Unregistering TweetUpdateReceiver.");
        LocalBroadcastManager.getInstance(this).unregisterReceiver(tweetUpdateReceiver);

        super.onPause();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (isBoundToTweetUpdates) {
            // TODO - This method was borrowed from Google... figure out what's happening here re: foreground
            // Unbind from the service. This signals to the service that this activity is no longer
            // in the foreground, and the service can respond by promoting itself to a foreground
            // service.
            Log.i(TAG, "Unbinding TweetUpdateService.");
            unbindService(tweetUpdateServiceConnection);
            isBoundToTweetUpdates = false;
        }
    }

    private void addMapButton() {
        Button goToMap = findViewById(R.id.button_id);
        goToMap.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent mapIntent = new Intent(getApplicationContext(), GoogleMapsActivity.class);
                startActivity(mapIntent);
            }
        });

    }

    private void repopulateFeed() {
        linearLayout.removeAllViews();
        for (Tweet tweet : tweets) {
            TweetView tweetView = new TweetView(FeedActivity.this, tweet);
            linearLayout.addView(tweetView);
        }
    }

    private void setButtonsState(boolean trackMeModeActive) {
        trackMeSwitch.setChecked(trackMeModeActive);
    }

    /**
     * Receiver for broadcasts sent by TweetUpdateService.
     */
    private class TweetUpdateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            tweets.clear();
            Gson gson = new Gson();
            ArrayList<String> tweetsAsStrings = intent.getStringArrayListExtra(TweetUpdateService.UPDATED_TWEETS);
            for (String tweetString : tweetsAsStrings) {
                Tweet tweet = gson.fromJson(tweetString, Tweet.class);
                tweets.add(tweet);
            }
            repopulateFeed();
            showContentFeed();
        }
    }
}
