/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.filter.story;


import java.util.Set;
import java.util.ArrayList;
import java.util.List;
import com.instagram.common.session.UserSession;

import app.morphe.extension.instagram.settings.SettingsStatus;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.entity.ReelResponseItem;
import app.morphe.extension.instagram.entity.UserData;

import app.morphe.extension.crimera.PikoUtils;

@SuppressWarnings("unused")
public class FilterStory {

    private static final java.util.concurrent.atomic.AtomicInteger parserCalls = new java.util.concurrent.atomic.AtomicInteger();
    private static final java.util.concurrent.atomic.AtomicInteger trayCalls = new java.util.concurrent.atomic.AtomicInteger();
    private static volatile String lastTrace = "";
    private static volatile String lastDisplayTrace = "";

    // The 449 main-feed adapter receives cached Reel models, not response items.
    // Copy only the displayed list; keep the repository and original models intact.
    public static List<?> filterDisplayReels(List<?> items, UserSession session) {
        if (items == null || items.isEmpty()) return items;
        Set<String> reelTypes = Pref.filterStoryByType();
        Set<String> userTypes = Pref.filterStoryByUserType();
        int minimum = Pref.filterStoryByMinStoryItems();
        int maximum = Pref.filterStoryByMaxStoryItems();
        if (minimum <= 1 && maximum >= 9999 && reelTypes.isEmpty() && userTypes.isEmpty()) return items;
        ArrayList<Object> result = new ArrayList<>(items.size());
        int knownCounts = 0;
        int unknownCounts = 0;
        for (Object item : items) {
            boolean keep = true;
            if (item != null && item.getClass().getName().equals("X.03wi")) {
                try {
                    Class<?> type = item.getClass();
                    if ("add_to_story".equals(type.getField("A2C").get(item))) {
                        result.add(item);
                        continue;
                    }
                    if (!reelTypes.isEmpty()) {
                        Object reelType = type.getField("A0V").get(item);
                        if (reelType != null && reelTypes.contains(reelType.toString())) keep = false;
                    }
                    if (keep && !userTypes.isEmpty()) {
                        Object user = type.getMethod("A0M").invoke(item);
                        if (user != null && userTypes.contains(new UserData(user).isVerified() ? "verified" : "unverified")) keep = false;
                    }
                    if (keep && (minimum > 1 || maximum < 9999)) {
                        Integer count = (Integer) type.getField("A0p").get(item);
                        if (count == null && session != null) {
                            int loadedCount = (Integer) type.getMethod("A0A", UserSession.class).invoke(item, session);
                            // An empty unloaded cache is not evidence of zero stories.
                            if (loadedCount > 0) count = loadedCount;
                        }
                        if (count == null) unknownCounts++;
                        else {
                            knownCounts++;
                            keep = count >= minimum && count <= maximum;
                        }
                    }
                } catch (Exception exception) {
                    // A changed native model must not remove unrelated tray entries.
                    keep = true;
                    unknownCounts++;
                }
            }
            if (keep) result.add(item);
        }
        if (Pref.pikoDebug()) {
            String trace = "story_filter ui input=" + items.size() + " output=" + result.size()
                    + " min=" + minimum + " max=" + maximum
                    + " knownCounts=" + knownCounts + " unknownCounts=" + unknownCounts;
            if (!trace.equals(lastDisplayTrace)) {
                lastDisplayTrace = trace;
                PikoUtils.logger(trace);
            }
        }
        return result.size() == items.size() ? items : result;
    }

    public static void traceDisplay() {
        if (!Pref.pikoDebug()) return;
        String state = "story_filter display min=" + Pref.filterStoryByMinStoryItems()
                + " max=" + Pref.filterStoryByMaxStoryItems()
                + " parserCalls=" + parserCalls.get() + " trayCalls=" + trayCalls.get();
        if (!state.equals(lastTrace)) {
            lastTrace = state;
            PikoUtils.logger(state);
        }
    }

    // Read the current selection when the native tray is delivered. Some 448 paths
    // use a Pando response or a cached response and never enter the JSON parser.
    public static ArrayList<?> filterTray(ArrayList<?> items) {
        trayCalls.incrementAndGet();
        if (items == null || items.isEmpty()) return items;
        Set<String> reelTypes = Pref.filterStoryByType();
        Set<String> userTypes = Pref.filterStoryByUserType();
        int minimum = Pref.filterStoryByMinStoryItems();
        int maximum = Pref.filterStoryByMaxStoryItems();
        ArrayList<Object> result = new ArrayList<>(items.size());
        for (Object item : items) {
            if (item == null || filter(item, reelTypes, userTypes, minimum, maximum) != null) {
                result.add(item);
            }
        }
        return result.size() == items.size() ? items : result;
    }

    public static Object filter(Object item) {
        parserCalls.incrementAndGet();
        return filter(item, Pref.filterStoryByType(), Pref.filterStoryByUserType(),
                Pref.filterStoryByMinStoryItems(), Pref.filterStoryByMaxStoryItems());
    }

    private static Object filter(Object item, Set<String> reelTypes, Set<String> userTypes,
                                 int minimum, int maximum) {
        if (item == null) return null;
        try {
            ReelResponseItem reel = new ReelResponseItem(item);
            // Unselected categories must not depend on optional native fields.
            if (!reelTypes.isEmpty() && reelTypes.contains(reel.getReelType())) return null;
            if (!userTypes.isEmpty()) {
                UserData user = reel.getUserData();
                if (user != null && userTypes.contains(user.isVerified() ? "verified" : "unverified")) {
                    return null;
                }
            }
            int count = reel.getMediaCount();
            if (count < minimum || count > maximum) return null;
        } catch (Exception exception) {
            // Preserve unknown items instead of breaking the complete story tray.
            PikoUtils.logger(exception.toString());
        }
        return item;
    }

}
