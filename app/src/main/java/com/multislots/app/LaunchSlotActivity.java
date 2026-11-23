package com.multislots.app;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Target of home-screen shortcuts: opens the app inside one slot and closes itself. */
public class LaunchSlotActivity extends Activity {
    static final String EXTRA_USER = "user_id";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int userId = getIntent().getIntExtra(EXTRA_USER, -1);
        SlotStore store = new SlotStore(this);
        if (userId < 0 || store.find(userId) == null || !Engine.launch(store.targetPackage(), userId)) {
            Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_LONG).show();
        }
        finish();
    }
}
