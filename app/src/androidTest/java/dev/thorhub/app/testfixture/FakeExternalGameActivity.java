package dev.thorhub.app.testfixture;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.stream.Collectors;

/**
 * Lives in the instrumentation APK, not the product APK, so the launch crosses
 * a package boundary like a real external game without requiring third-party
 * software on the emulator. It intentionally uses only framework/Java APIs so
 * the test APK can run in its own process without the target APK classpath.
 */
public final class FakeExternalGameActivity extends Activity {
    public static final String EXTRA_REPORT_ACTION = "report_action";
    public static final String EXTRA_REPORT_PACKAGE = "report_package";
    public static final String EXTRA_SLOT = "slot";
    public static final String EXTRA_RESUME = "resume";
    public static final String EXTRA_LABELS = "labels";

    public static final String EXTRA_OBSERVED_ACTION = "observed_action";
    public static final String EXTRA_OBSERVED_DATA = "observed_data";
    public static final String EXTRA_OBSERVED_CLIP_URI = "observed_clip_uri";
    public static final String EXTRA_OBSERVED_DISPLAY_ID = "observed_display_id";
    public static final String EXTRA_OBSERVED_FLAGS = "observed_flags";
    public static final String EXTRA_OBSERVED_SLOT = "observed_slot";
    public static final String EXTRA_OBSERVED_RESUME = "observed_resume";
    public static final String EXTRA_OBSERVED_LABELS = "observed_labels";
    public static final String EXTRA_OBSERVED_CONTENT = "observed_content";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent launch = getIntent();
        String reportAction = launch.getStringExtra(EXTRA_REPORT_ACTION);
        String reportPackage = launch.getStringExtra(EXTRA_REPORT_PACKAGE);
        if (reportAction != null && reportPackage != null) {
            Uri data = launch.getData();
            String readablePayload = readPayload(data);
            String clipUri = null;
            if (launch.getClipData() != null && launch.getClipData().getItemCount() > 0) {
                Uri observed = launch.getClipData().getItemAt(0).getUri();
                clipUri = observed == null ? null : observed.toString();
            }
            ArrayList<String> labels = launch.getStringArrayListExtra(EXTRA_LABELS);
            Intent report = new Intent(reportAction)
                .setPackage(reportPackage)
                .putExtra(EXTRA_OBSERVED_ACTION, launch.getAction())
                .putExtra(EXTRA_OBSERVED_DATA, launch.getDataString())
                .putExtra(EXTRA_OBSERVED_CLIP_URI, clipUri)
                .putExtra(EXTRA_OBSERVED_DISPLAY_ID, getDisplay() == null ? -1 : getDisplay().getDisplayId())
                .putExtra(EXTRA_OBSERVED_FLAGS, launch.getFlags())
                .putExtra(EXTRA_OBSERVED_SLOT, launch.getIntExtra(EXTRA_SLOT, -1))
                .putExtra(EXTRA_OBSERVED_RESUME, launch.getBooleanExtra(EXTRA_RESUME, false))
                .putStringArrayListExtra(EXTRA_OBSERVED_LABELS, labels)
                .putExtra(EXTRA_OBSERVED_CONTENT, readablePayload);
            sendBroadcast(report);
        }
        finish();
    }

    private String readPayload(Uri data) {
        if (data == null) return null;
        try (InputStream input = getContentResolver().openInputStream(data)) {
            if (input == null) return null;
            try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8)
            )) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (Exception error) {
            return "ERROR:" + error.getClass().getSimpleName();
        }
    }
}
