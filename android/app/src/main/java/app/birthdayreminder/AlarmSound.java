package app.birthdayreminder;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;

import java.io.File;

/** Prepares a MediaPlayer for an alarm sound: a user-picked local file, or the system default alarm tone. */
final class AlarmSound {
    private AlarmSound() {}

    /** Returns a prepared (not yet started) player, or null if nothing could be played. */
    static MediaPlayer prepare(Context ctx, String path, boolean loop) {
        MediaPlayer mp = null;
        if (path != null && !path.isEmpty() && new File(path).exists()) {
            mp = tryPrepare(ctx, path, null, loop);
        }
        if (mp == null) {
            mp = tryPrepare(ctx, null, defaultUri(), loop);
        }
        return mp;
    }

    private static Uri defaultUri() {
        Uri u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        if (u == null) u = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
        return u;
    }

    private static MediaPlayer tryPrepare(Context ctx, String path, Uri uri, boolean loop) {
        MediaPlayer mp = new MediaPlayer();
        try {
            mp.setAudioAttributes(new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build());
            if (path != null) {
                mp.setDataSource(path);
            } else if (uri != null) {
                mp.setDataSource(ctx.getApplicationContext(), uri);
            } else {
                mp.release();
                return null;
            }
            mp.setLooping(loop);
            mp.prepare();
            return mp;
        } catch (Exception e) {
            try { mp.release(); } catch (Exception ignored) {}
            return null;
        }
    }
}
