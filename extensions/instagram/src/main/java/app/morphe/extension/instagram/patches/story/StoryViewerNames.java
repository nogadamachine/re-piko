package app.morphe.extension.instagram.patches.story;

import android.text.TextUtils;
import android.text.method.TransformationMethod;
import android.view.View;
import android.widget.TextView;
import java.util.WeakHashMap;
import app.morphe.extension.instagram.entity.Entity;
import app.morphe.extension.instagram.entity.UserData;
import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.shared.Logger;

/** Only called by the two story dashboard viewer binders, after native binding. */
public final class StoryViewerNames {
    private static final WeakHashMap<TextView, Layout> layouts = new WeakHashMap<>();

    private StoryViewerNames() { }

    public static void bindViewer(View row, Object viewer) {
        // This adapter also binds section headers and aggregate rows. Never
        // cast those models to a viewer or replace their labels.
        if (viewer == null || !"X.0D06".equals(viewer.getClass().getName())) return;
        try {
            bind(row, new Entity(viewer).getField("A07"), 0x7f0b3833);
        } catch (Exception e) {
            Logger.printException(() -> "Unable to read story viewer metadata", e);
        }
    }

    public static void bind(View row, Object user, int titleId) {
        if (row == null) return;
        View title = row.findViewById(titleId);
        if (title instanceof TextView) bindTitle((TextView) title, user);
    }

    public static void bindTitle(TextView title, Object user) {
        if (title == null) return;
        // Native binding has already restored the text. Also restore any layout
        // change before applying the current preference to a recycled row.
        Layout previous = layouts.remove(title);
        if (previous != null) previous.restore(title);
        if (!Pref.storyViewerNames() || user == null) return;
        try {
            UserData data = new UserData(user);
            String label = ViewerLabel.format(data.getUsername(), data.getFullName());
            if (label == null) return;
            layouts.put(title, new Layout(title));
            title.setSingleLine(false);
            title.setMaxLines(2);
            title.setEllipsize(TextUtils.TruncateAt.END);
            title.setText(label);
        } catch (Exception e) {
            Logger.printException(() -> "Unable to format story viewer name", e);
        }
    }

    private static final class Layout {
        final int maxLines;
        final TextUtils.TruncateAt ellipsize;
        final TransformationMethod transformation;

        Layout(TextView title) {
            maxLines = title.getMaxLines();
            ellipsize = title.getEllipsize();
            transformation = title.getTransformationMethod();
        }

        void restore(TextView title) {
            title.setSingleLine(maxLines == 1);
            title.setMaxLines(maxLines);
            title.setEllipsize(ellipsize);
            title.setTransformationMethod(transformation);
        }
    }
}
