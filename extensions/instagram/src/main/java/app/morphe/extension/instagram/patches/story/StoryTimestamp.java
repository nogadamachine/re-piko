/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */

package app.morphe.extension.instagram.patches.story;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import app.morphe.extension.instagram.utils.Pref;
import app.morphe.extension.instagram.utils.IgStr;
import app.morphe.extension.shared.Logger;

public class StoryTimestamp {
    public static String customiseStoryTimestamp(long postedTS) {
        String formatedTS = null;

        try {
            String mode = Pref.customiseStoryTimestamp();
            postedTS = postedTS * 1000;

            if ("detailed".equals(mode)) {
                Date date = new Date(postedTS);
                SimpleDateFormat format =
                        new SimpleDateFormat(IgStr.str("piko_story_timestamp_detailed_format"), Locale.getDefault());
                formatedTS = format.format(date);

            } else if ("posttime".equals(mode)) {
                Date date = new Date(postedTS);
                SimpleDateFormat format =
                        new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                formatedTS = format.format(date);

            } else if ("timeleft".equals(mode)) {
                long now = System.currentTimeMillis();
                long expireTS = postedTS + 86400000L; // +24 hours
                long diff = expireTS - now;

                if (diff <= 0) {
                    formatedTS = IgStr.str("piko_story_timestamp_expired");
                } else {
                    long totalSeconds = diff / 1000;

                    long hours = totalSeconds / 3600;
                    long minutes = (totalSeconds % 3600) / 60;
                    long seconds = totalSeconds % 60;

                    formatedTS = String.format(Locale.getDefault(),
                            IgStr.str("piko_story_timestamp_remaining"), hours, minutes, seconds);
                }
            }

        } catch (Exception e) {
            Logger.printException(() -> "customiseStoryTimestamp failure", e);
        }

        return formatedTS;
    }
}
