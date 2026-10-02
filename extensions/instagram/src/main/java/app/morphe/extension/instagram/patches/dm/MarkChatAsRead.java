/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */


package app.morphe.extension.instagram.patches.dm;

import java.util.List;
import android.content.Context;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.ObjectBrowser;

import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.MarkChatAsReadScope;

import com.instagram.model.direct.DirectThreadKey;
import com.instagram.common.session.UserSession;


@SuppressWarnings("unused")
public class MarkChatAsRead {

    private static final PendingReadCache<Object[]> pendingReads = new PendingReadCache<>(16, 900_000L);
    private static final PendingReadCache<PendingVisualReads> pendingVisualReads = new PendingReadCache<>(16, 900_000L);

    /** The native visual viewer sends a separate receipt and mutates its own seen cache. */
    public static boolean shouldBlockNativeVisualRead(Object media, Object controller) {
        boolean blocked = Pref.viewDmAnonymously();
        if (!blocked || MarkChatAsReadScope.isActive() || !Pref.enableMarkChatAsReadOption()
                || media == null || controller == null) return blocked;
        try {
            Entity reflection = new Entity();
            Object session = reflection.getField(controller, "A0Q");
            DirectThreadKey key = (DirectThreadKey) reflection.getField(controller, "A0g");
            String item = (String) reflection.getField(media, "A0J");
            if (key == null || item == null || item.isEmpty()) return blocked;
            long now = android.os.SystemClock.elapsedRealtime();
            PendingVisualReads batch = pendingVisualReads.get(session, key.A00, now);
            if (batch == null) batch = new PendingVisualReads(32, 900_000L);
            batch.put(item, media, controller, now);
            pendingVisualReads.put(session, key.A00, now, now, batch);
            if (Pref.pikoDebug()) android.util.Log.d("piko", "visual-seen: captured blocked=" + blocked);
        } catch (Exception e) {
            if (Pref.pikoDebug()) android.util.Log.d("piko", "visual-seen: capture unavailable");
        }
        return blocked;
    }

    /** Capture before native optimistic seen state changes, without sending anything. */
    public static boolean shouldBlockNativeRead(Object dispatcher, Object thread, Object pair,
                                                Object key, boolean force) {
        boolean blocked = Pref.viewDmAnonymously();
        if (!blocked || MarkChatAsReadScope.isActive() || !Pref.enableMarkChatAsReadOption()
                || pair == null || key == null) return blocked;
        try {
            Entity reflection = new Entity();
            Object session = reflection.getField(dispatcher, "A00");
            String threadId = (String) reflection.getField(key, "A00");
            Object main = reflection.getField(pair, "A01");
            Object regular = reflection.getField(pair, "A00");
            long mainTime = itemTimestamp(reflection, main);
            long regularTime = itemTimestamp(reflection, regular);
            if (main == null || mainTime <= 0) return blocked;
            pendingReads.put(session, threadId, Math.max(mainTime, regularTime),
                    android.os.SystemClock.elapsedRealtime(), new Object[] {dispatcher, thread, pair, key});
            if (Pref.pikoDebug()) android.util.Log.d("piko", "dm-seen: captured main="
                    + mainTime + ", regular=" + regularTime);
        } catch (Exception e) {
            if (Pref.pikoDebug()) android.util.Log.d("piko", "dm-seen: capture unavailable");
        }
        return blocked;
    }

    private static long itemTimestamp(Entity reflection, Object item) throws Exception {
        return item == null ? 0L : (Long) reflection.getMethod(item, "A0J", new Class<?>[0], new Object[0]);
    }

    private static String getButtonEnumClassName(){
        return "className";
    }

    private static Object getButton(String enumTag) throws Exception {
        Class<?> targetClass = Class.forName(MarkChatAsRead.getButtonEnumClassName());
        Class<?>[] paramTypes = new Class<?>[] {String.class};

        Entity entity = new Entity();
        return entity.getMethod(
                targetClass,
                "valueOf",
                paramTypes,
                enumTag
        );

    }

    /** Replays the native request for this login session and the latest viewed message.
     * The inbox selector can return older visual media instead of newer text messages.
     * Without a recent captured request, require the user to open the conversation first.
     */
    private static boolean dispatchNativeRead(UserSession session, DirectThreadKey key) throws Exception {
        if (key == null || key.A00 == null || key.A00.isEmpty()) return false;
        Entity reflection = new Entity();
        boolean dispatched = false;
        long now = android.os.SystemClock.elapsedRealtime();
        PendingVisualReads batch = pendingVisualReads.get(session, key.A00, now);
        if (batch != null) {
            Class<?> controller = Class.forName("instagram.features.direct.visual.internal.DirectVisualMessageViewerController");
            for (PendingVisualReads.Request visual : batch.requests(now)) {
                reflection.getMethod(controller, "A0C",
                        new Class<?>[] {Class.forName("X.0Y3J"), controller},
                        visual.media(), visual.controller());
                batch.remove(visual);
                dispatched = true;
                if (Pref.pikoDebug()) android.util.Log.d("piko", "manual-read: replayed viewed native visual request");
            }
        }
        Object[] pending = pendingReads.get(session, key.A00, android.os.SystemClock.elapsedRealtime());
        if (pending != null) {
            reflection.getMethod(pending[0], "A02",
                    new Class<?>[] {Class.forName("X.0Uhg"), Class.forName("X.06i8"),
                            Class.forName("X.07gZ"), boolean.class},
                    pending[1], pending[2], pending[3], true);
            // Match Instagram's explicit mark-read action, including its local unread state.
            Object store = reflection.getField(pending[0], "A03");
            reflection.getMethod(store, "AMg", new Class<?>[] {DirectThreadKey.class}, key);
            pendingReads.remove(session, key.A00, pending);
            if (Pref.pikoDebug()) android.util.Log.d("piko", "manual-read: replayed visible native request");
            return true;
        }
        return dispatched;
    }

    private static void markAsRead(UserSession session, DirectThreadKey key) {
        try {
            if (Pref.pikoDebug()) android.util.Log.d("piko", "manual-read: selected");
            MarkChatAsReadScope.run(() -> {
                boolean dispatched = dispatchNativeRead(session, key);
                if (Pref.pikoDebug()) android.util.Log.d("piko", "manual-read: native request dispatched=" + dispatched);
                if (!dispatched) app.morphe.extension.shared.Utils.showToastShort(
                        app.morphe.extension.instagram.utils.IgStr.str("piko_manual_read_unavailable"));
            });
        } catch (Exception e) {
            PikoUtils.logger(e);
            app.morphe.extension.shared.Utils.showToastShort(
                    app.morphe.extension.instagram.utils.IgStr.str("piko_manual_read_unavailable"));
        }
    }


    public static void logMutationState(String state) {
        if (!Pref.pikoDebug()) return;
        String safeState = "uploaded".equals(state) || "upload_failed_transient".equals(state)
                || "upload_failed_permanent".equals(state) ? state : "other";
        android.util.Log.d("piko", "dm-seen: mutation-state=" + safeState);
    }

    public static List addButton(List buttonList){
        try{
            if(Pref.pikoDebug()){
                buttonList.add(getButton("THREAD_LEVEL_DEBUG"));
            }
            if(Pref.enableMarkChatAsReadOption()){
                buttonList.add(getButton("MARK_AS_READ"));
            }

        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return buttonList;
    }

    // Return true = skip other button press check.
    // Return false = other button press check.
    public static boolean buttonAction(Context context, UserSession userSession,Object buttonPressed, Object unknown, DirectThreadKey directThreadKey){
        try {
            String buttonEnumTag = buttonPressed.toString();

            if(buttonEnumTag.equals("MARK_AS_READ")){
                MarkChatAsRead.markAsRead(userSession, directThreadKey);
                return true;
            } else if(buttonEnumTag.equals("THREAD_LEVEL_DEBUG")){
                ObjectBrowser.browseObject(context, unknown);
                return true;
            }
        } catch (Exception e) {
            PikoUtils.logger(e);
        }
        return false;
    }
}
