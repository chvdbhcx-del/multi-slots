package com.multislots.app;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Keeps the list of slots (virtual user id + the name the person gave it). */
final class SlotStore {
    static final String DEFAULT_PACKAGE = "app.source.getcontact";

    static final class Slot {
        final int userId;
        String name;

        Slot(int userId, String name) {
            this.userId = userId;
            this.name = name;
        }
    }

    private final SharedPreferences prefs;

    SlotStore(Context c) {
        prefs = c.getSharedPreferences("slots", Context.MODE_PRIVATE);
    }

    String targetPackage() {
        return prefs.getString("pkg", DEFAULT_PACKAGE);
    }

    void setTargetPackage(String pkg) {
        prefs.edit().putString("pkg", pkg).apply();
    }

    List<Slot> load() {
        List<Slot> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs.getString("list", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Slot(o.getInt("id"), o.optString("name")));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    void save(List<Slot> slots) {
        JSONArray a = new JSONArray();
        try {
            for (Slot s : slots) a.put(new JSONObject().put("id", s.userId).put("name", s.name));
        } catch (Exception ignored) {
        }
        prefs.edit().putString("list", a.toString()).apply();
    }

    /** Next free virtual user id. Ids are never reused, so a deleted slot's leftovers can't leak into a new one. */
    int nextUserId() {
        int next = prefs.getInt("next", 0);
        prefs.edit().putInt("next", next + 1).apply();
        return next;
    }

    Slot find(int userId) {
        for (Slot s : load()) if (s.userId == userId) return s;
        return null;
    }
}
