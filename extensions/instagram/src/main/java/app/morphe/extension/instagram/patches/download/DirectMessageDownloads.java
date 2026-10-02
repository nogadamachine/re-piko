/*
 * Copyright (C) 2026 piko <https://github.com/crimera/piko>
 *
 * See the included NOTICE file for GPLv3 §7(b) terms that apply to this code.
 */
package app.morphe.extension.instagram.patches.download;

import static app.morphe.extension.instagram.utils.IgStr.str;

import android.app.Dialog;
import android.content.Context;
import java.util.ArrayList;
import java.util.List;

import app.morphe.extension.crimera.PikoUtils;
import app.morphe.extension.crimera.downloader.DownloadRequest;
import app.morphe.extension.crimera.downloader.MediaDownloader;
import app.morphe.extension.instagram.constants.Constants;
import app.morphe.extension.instagram.entity.InstagramDialogBox;
import app.morphe.extension.instagram.entity.MediaData;
import app.morphe.extension.instagram.entity.MediaInterface;
import app.morphe.extension.instagram.entity.MediaVariantSelector;
import app.morphe.extension.shared.Utils;

/** Uses the original media responses for regular and one/two-view DM attachments. */
public final class DirectMessageDownloads {
    private DirectMessageDownloads() {}

    public static boolean show(Context context, MediaData media) throws Exception {
        boolean video = media.isVideo();
        List<MediaInterface> variants = video ? media.getVideoVariants() : media.getImageVariants();
        MediaInterface best = MediaVariantSelector.highestResolution(variants);
        if (best == null) return false;
        String url = best.getUrl();
        String id = media.getMediaPkId();
        String filename = "DM_" + (id == null || id.isEmpty() ? System.currentTimeMillis() : id)
                + (video ? ".mp4" : ".jpg");
        String safeName = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        InstagramDialogBox dialog = new InstagramDialogBox(context);
        dialog.setTitle(str("piko_download_options"));
        dialog.addDialogMenuItems(new String[]{str("piko_download_current_media"),
                str(video ? "piko_video_variants" : "piko_image_variants"), str("piko_copy_media_link")},
                (d, which) -> {
                    if (which == 0) save(context, url, safeName);
                    else if (which == 1) showVariants(context, variants, safeName, video);
                    else Utils.setClipboard(url);
                });
        finishDialog(dialog);
        return true;
    }

    private static void showVariants(Context context, List<MediaInterface> variants, String name, boolean video) {
        List<MediaInterface> available = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        try {
            for (MediaInterface variant : variants) {
                if (variant == null || variant.getUrl() == null || variant.getUrl().isEmpty()) continue;
                available.add(variant);
                labels.add(variant.getVariantTag());
            }
            InstagramDialogBox dialog = new InstagramDialogBox(context);
            dialog.setTitle(str(video ? "piko_video_variants" : "piko_image_variants"));
            dialog.addDialogMenuItems(labels.toArray(new String[0]), (d, which) -> {
                try { save(context, available.get(which).getUrl(), name); }
                catch (Exception e) { report(e); }
            });
            finishDialog(dialog);
        } catch (Exception e) { report(e); }
    }

    private static void save(Context context, String url, String name) {
        // Do not attach metadata or remux: video downloads must retain the response bytes.
        new MediaDownloader(context).enqueue(new DownloadRequest(url, Constants.DEFAULT_DM_FOLDER, name));
    }

    private static void finishDialog(InstagramDialogBox dialog) {
        dialog.setNegativeButton(str("piko_close"), (d, which) -> d.dismiss());
        dialog.setCancelable(true);
        dialog.setCanceledOnTouchOutside(true);
        Dialog nativeDialog = dialog.getDialog();
        nativeDialog.show();
    }

    private static void report(Exception error) {
        PikoUtils.logger(error);
        Utils.showToastShort(error.getMessage());
    }
}
