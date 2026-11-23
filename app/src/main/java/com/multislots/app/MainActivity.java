package com.multislots.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());
    private SlotStore store;
    private List<SlotStore.Slot> slots = new ArrayList<>();
    private SlotAdapter adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        store = new SlotStore(this);
        ListView list = findViewById(R.id.list);
        empty = findViewById(R.id.empty);
        adapter = new SlotAdapter();
        list.setAdapter(adapter);
        list.setOnItemClickListener((p, v, pos, id) -> open(slots.get(pos)));
        list.setOnItemLongClickListener((p, v, pos, id) -> {
            actions(slots.get(pos));
            return true;
        });
        Button add = findViewById(R.id.add);
        add.setOnClickListener(v -> addSlot());
        findViewById(R.id.settings).setOnClickListener(v -> editPackage());
        refresh();
    }

    private void refresh() {
        slots = store.load();
        adapter.notifyDataSetChanged();
        empty.setVisibility(slots.isEmpty() ? View.VISIBLE : View.GONE);
    }

    private boolean targetInstalledOnPhone() {
        try {
            getPackageManager().getPackageInfo(store.targetPackage(), 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private void addSlot() {
        if (!targetInstalledOnPhone()) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.no_target_title)
                    .setMessage(getString(R.string.no_target_msg, store.targetPackage()))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        final EditText name = new EditText(this);
        name.setHint(R.string.slot_name_hint);
        name.setText(getString(R.string.slot_default_name, slots.size() + 1));
        new AlertDialog.Builder(this)
                .setTitle(R.string.add_slot)
                .setView(name)
                .setPositiveButton(R.string.create, (d, w) -> createSlot(name.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @SuppressWarnings("deprecation")
    private void createSlot(final String name) {
        final int userId = store.nextUserId();
        final String pkg = store.targetPackage();
        final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.installing), true, false);
        io.execute(() -> {
            final String err = Engine.install(pkg, userId);
            main.post(() -> {
                pd.dismiss();
                if (err != null) {
                    Toast.makeText(this, getString(R.string.install_failed, err), Toast.LENGTH_LONG).show();
                    return;
                }
                List<SlotStore.Slot> l = store.load();
                l.add(new SlotStore.Slot(userId, name.isEmpty() ? getString(R.string.slot_default_name, l.size() + 1) : name));
                store.save(l);
                refresh();
                Toast.makeText(this, R.string.slot_ready, Toast.LENGTH_LONG).show();
            });
        });
    }

    private void open(SlotStore.Slot s) {
        final String pkg = store.targetPackage();
        io.execute(() -> {
            if (!Engine.isInstalled(pkg, s.userId)) {
                String err = Engine.install(pkg, s.userId);
                if (err != null) {
                    main.post(() -> Toast.makeText(this, getString(R.string.install_failed, err), Toast.LENGTH_LONG).show());
                    return;
                }
            }
            if (!Engine.launch(pkg, s.userId)) {
                main.post(() -> Toast.makeText(this, R.string.launch_failed, Toast.LENGTH_LONG).show());
            }
        });
    }

    private void actions(final SlotStore.Slot s) {
        String[] items = {
                getString(R.string.rename),
                getString(R.string.shortcut),
                getString(R.string.update_app),
                getString(R.string.delete)
        };
        new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setItems(items, (d, which) -> {
                    if (which == 0) rename(s);
                    else if (which == 1) pinShortcut(s);
                    else if (which == 2) updateApp(s);
                    else confirmDelete(s);
                })
                .show();
    }

    private void rename(final SlotStore.Slot s) {
        final EditText name = new EditText(this);
        name.setText(s.name);
        new AlertDialog.Builder(this)
                .setTitle(R.string.rename)
                .setView(name)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    List<SlotStore.Slot> l = store.load();
                    for (SlotStore.Slot x : l) if (x.userId == s.userId) x.name = name.getText().toString().trim();
                    store.save(l);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    /** Re-copies the phone's current version of the app into the slot; the slot's data and login are kept. */
    @SuppressWarnings("deprecation")
    private void updateApp(final SlotStore.Slot s) {
        final String pkg = store.targetPackage();
        final ProgressDialog pd = ProgressDialog.show(this, null, getString(R.string.installing), true, false);
        io.execute(() -> {
            final String err = Engine.install(pkg, s.userId);
            main.post(() -> {
                pd.dismiss();
                Toast.makeText(this, err == null ? getString(R.string.updated) : getString(R.string.install_failed, err),
                        Toast.LENGTH_LONG).show();
            });
        });
    }

    private void confirmDelete(final SlotStore.Slot s) {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_title, s.name))
                .setMessage(R.string.delete_msg)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    final String pkg = store.targetPackage();
                    io.execute(() -> Engine.remove(pkg, s.userId));
                    List<SlotStore.Slot> l = store.load();
                    for (int i = l.size() - 1; i >= 0; i--) if (l.get(i).userId == s.userId) l.remove(i);
                    store.save(l);
                    refresh();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void pinShortcut(SlotStore.Slot s) {
        if (Build.VERSION.SDK_INT < 26) {
            Toast.makeText(this, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        ShortcutManager sm = getSystemService(ShortcutManager.class);
        if (sm == null || !sm.isRequestPinShortcutSupported()) {
            Toast.makeText(this, R.string.shortcut_unsupported, Toast.LENGTH_LONG).show();
            return;
        }
        Intent i = new Intent(this, LaunchSlotActivity.class)
                .setAction(Intent.ACTION_VIEW)
                .putExtra(LaunchSlotActivity.EXTRA_USER, s.userId);
        ShortcutInfo info = new ShortcutInfo.Builder(this, "slot_" + s.userId)
                .setShortLabel(s.name.isEmpty() ? "#" + (s.userId + 1) : s.name)
                .setIcon(Icon.createWithResource(this, R.drawable.ic_slots))
                .setIntent(i)
                .build();
        sm.requestPinShortcut(info, null);
    }

    private void editPackage() {
        final EditText pkg = new EditText(this);
        pkg.setText(store.targetPackage());
        new AlertDialog.Builder(this)
                .setTitle(R.string.target_package)
                .setMessage(R.string.target_package_msg)
                .setView(pkg)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String v = pkg.getText().toString().trim();
                    store.setTargetPackage(v.isEmpty() ? SlotStore.DEFAULT_PACKAGE : v);
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private final class SlotAdapter extends BaseAdapter {
        @Override public int getCount() { return slots.size(); }
        @Override public Object getItem(int p) { return slots.get(p); }
        @Override public long getItemId(int p) { return slots.get(p).userId; }

        @Override
        public View getView(int p, View v, ViewGroup parent) {
            if (v == null) v = getLayoutInflater().inflate(R.layout.item_slot, parent, false);
            SlotStore.Slot s = slots.get(p);
            ((TextView) v.findViewById(R.id.badge)).setText(String.valueOf(p + 1));
            ((TextView) v.findViewById(R.id.name)).setText(s.name);
            ((TextView) v.findViewById(R.id.sub)).setText(getString(R.string.slot_sub, s.userId + 1));
            return v;
        }
    }
}
