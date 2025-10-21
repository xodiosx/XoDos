package com.termux.app.terminal;
import android.util.DisplayMetrics;
import com.termux.app.XoDosShortcuts;
import static com.termux.shared.termux.TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH;
import android.content.res.AssetManager;
import android.widget.GridLayout;
import android.widget.PopupWindow;
import android.view.Gravity;
import android.widget.ScrollView;
import com.termux.app.terminal.utils.ScreenUtils;
import android.widget.ImageView;
import android.widget.TextView;
import com.termux.app.terminal.utils.FilePathUtils; ///////
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;
// Add with other imports
// Add these imports at the top
import com.termux.app.XoDosFileManager;
import android.util.Log;
import static com.termux.shared.termux.TermuxConstants.TERMUX_FILES_DIR_PATH;
import java.util.HashMap;
import java.util.Map;


import android.app.ProgressDialog;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import android.view.Window;
import android.view.WindowManager;
import android.content.DialogInterface;
import android.graphics.drawable.ColorDrawable;


import android.app.AlertDialog;
import android.content.Intent;
import java.io.File;
import org.json.JSONArray;
import org.json.JSONObject;
import android.content.Context;
import android.net.Uri;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
// Add with other imports
import android.graphics.Color;
import android.util.TypedValue;
import android.text.TextUtils;
import android.graphics.Typeface;
//////////
import android.os.Handler;
import com.termux.app.XoDosWizard;

import static com.termux.shared.termux.TermuxConstants.TERMUX_HOME_DIR_PATH;
import org.json.JSONException;
import com.termux.x11.controller.core.FileUtils;


import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.termux.R;
import com.termux.app.TermuxActivity;

import java.io.File;
import java.util.ArrayList;
// --- Add at top of StartEntryClient.java ---
import java.io.File;
import java.io.FileReader;
import java.io.BufferedReader;
import java.io.IOException;
// Add SharedPreferences import
import android.content.SharedPreferences;


///
public class StartEntryClient implements FileBrowser.FileSlectedAdapter{

// Add these class members
private View mBlockingView;
private final Handler mHandler = new Handler();
public static final int FILE_REQUEST_WINE_CODE = 1002;
private int mTutorialStep = 0;
private AlertDialog mTutorialDialog;
// Add with other class members
private ProgressDialog mRestoreProgressDialog;
private boolean mRestoreInProgress = false;


private PopupWindow mToolboxPopup;
private GridLayout mToolboxGrid;
private View mConfigPopupContent;

    private TermuxActivity mTermuxActivity;
    private TermuxTerminalSessionActivityClient mTermuxTerminalSessionActivityClient;
    private LinearLayout mStartItemEntriesConfig;
    private Button mLaunchButton;
    private ImageButton mSelectConfigButton;
    private ImageButton mAddConfigButton;
    private ImageButton mRemoveConfigButton;
    private Spinner mLaunchItemSpinner;
    private ArrayList<StartEntry.Entry> mEntries;
    private StartEntryArrayAdapter mAdapter;
    private int mCurrentCommand;
    private FileBrowser mFileBrowser;

    // Tutorial tracking members
    private SharedPreferences mSharedPreferences;
    private static final String PREF_NAME = "termux_toolbox_prefs";
    private static final String KEY_TUTORIAL_OPENED = "tutorial_opened";
    private Handler mFlashHandler = new Handler();
    private Runnable mFlashRunnable;
    private boolean mIsFlashing = false;
    private LinearLayout mHelpButton;

    //// helper ////////
// Add this method any
private void showRestoreProgressDialog() {
    mRestoreProgressDialog = new ProgressDialog(mTermuxActivity);
    mRestoreProgressDialog.setTitle(" Installing,,,");
    mRestoreProgressDialog.setMessage("Preparing...");
    mRestoreProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
    mRestoreProgressDialog.setMax(100);
    mRestoreProgressDialog.setCancelable(false);
    mRestoreProgressDialog.show();
    mRestoreInProgress = true;
}
private static final String USR_PREFIX = "/data/data/com.termux/files/usr";
private String winePrefixFromConf = "";

// Add this method in the class
private void handleRestoreError(Exception e) {
    mTermuxActivity.runOnUiThread(() -> {
        new AlertDialog.Builder(mTermuxActivity)
            .setTitle("🚫 Error")
            .setMessage(e.getMessage())
            .setPositiveButton(android.R.string.ok, null)
            .show();
    });
}


private void dismissRestoreProgress() {
    mTermuxActivity.runOnUiThread(() -> {
        if(mRestoreProgressDialog != null && mRestoreProgressDialog.isShowing()) {
            mRestoreProgressDialog.dismiss();
        }
        mRestoreInProgress = false;
    });
}

public void handleRestoreBackup(Uri backupUri) {
    new Thread(() -> {
        File tempBackupFile = null;
        try {
            // Phase 1: Copy selected backup file with progress (0-50%)
            updateRestoreProgress(0, "Loading archive file...");
            tempBackupFile = copyBackupToTemp(backupUri);

                 // Phase 2: Extract (50-100%)
            updateRestoreProgress(50, "installing files...");
            extractBackupFile(tempBackupFile);

            // Show completion message for 2 seconds before closing
            updateRestoreProgress(100, "✅complete!");
            mTermuxActivity.runOnUiThread(() -> {
                new Handler().postDelayed(() -> {
                    if (mRestoreProgressDialog != null && mRestoreProgressDialog.isShowing()) {
                    //// Exit the app completely
                mTermuxActivity.finishAffinity();
                System.exit(0);
                
                        mRestoreProgressDialog.dismiss();
                    }
                }, 3000); // 
            });

            
        } catch (Exception e) {
            handleRestoreError(e);
        } finally {
            if (tempBackupFile != null) tempBackupFile.delete();
        }
    }).start();
}

private File copyBackupToTemp(Uri backupUri) throws IOException {
    File tempFile = new File(mTermuxActivity.getFilesDir(), "restore_temp.tar.xz");
    
    try (InputStream in = mTermuxActivity.getContentResolver().openInputStream(backupUri);
         FileOutputStream out = new FileOutputStream(tempFile)) {
        
        // Same buffer size as installer (128KB)
        byte[] buffer = new byte[1024 * 128];
        long totalBytes = mTermuxActivity.getContentResolver()
            .openAssetFileDescriptor(backupUri, "r").getLength();
        long copiedBytes = 0;
        
        int bytesRead;
        while ((bytesRead = in.read(buffer)) != -1) {
            out.write(buffer, 0, bytesRead);
            copiedBytes += bytesRead;
            int progress = (int) ((copiedBytes * 50) / totalBytes);
            updateRestoreProgress(progress, "📲Copying backup...");
        }
    }
    return tempFile;
}

private void extractBackupFile(File backupFile) throws Exception {
    // Same checkpoint calculation as original installer
    long totalBytes = backupFile.length();
    int bytesPerRecord = 512;
    long totalRecords = (totalBytes + bytesPerRecord - 1) / bytesPerRecord;
    int targetCheckpoints = 250;
    int recordsPerCheckpoint = Math.max(1, (int) (totalRecords / targetCheckpoints));

    // Identical environment setup
    Map<String, String> env = new HashMap<>();
    env.put("PATH", TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
    env.put("LD_LIBRARY_PATH", TERMUX_BIN_PREFIX_DIR_PATH.replace("bin", "lib"));

    // Mirror original installer's tar command
ProcessBuilder processBuilder = new ProcessBuilder()
    .command(
        "sh", "-c",
        TERMUX_BIN_PREFIX_DIR_PATH + "/tar -xf " + backupFile.getAbsolutePath() +
        " -C " + TERMUX_FILES_DIR_PATH +
        " --preserve-permissions" +
        " --warning=no-file-ignored" +
        " --checkpoint=" + recordsPerCheckpoint +
        " --checkpoint-action=echo=CHECKPOINT" +
        " --totals" +
        " 2>&1"
    )
    .redirectErrorStream(true);

// Set environment variables first
processBuilder.environment().putAll(env);

// Then start the process
Process process = processBuilder.start();
    // Same progress tracking logic
    BufferedReader reader = new BufferedReader(
        new InputStreamReader(process.getInputStream()));
    
    int checkpointCount = 0;
    String line;
    while ((line = reader.readLine()) != null) {
        if (line.contains("CHECKPOINT")) {
            checkpointCount++;
            int progress = 50 + (int) ((checkpointCount * 50.0) / 50);
            progress = Math.min(progress, 99);
            updateRestoreProgress(progress, "Extracting: " + progress + "%");
        }
    }

    int exitCode = process.waitFor();
    if (exitCode != 0) {
        throw new IOException("Restore failed (code " + exitCode + "). Possible causes:\n" +
            "1. Corrupted backup file\n" +
            "2. Insufficient storage\n" +
            "3. Invalid file permissions");
    }
}

// Progress update helper (keep UI thread safe)
private void updateRestoreProgress(int progress, String message) {
    mTermuxActivity.runOnUiThread(() -> {
        if (mRestoreProgressDialog != null && mRestoreProgressDialog.isShowing()) {
            mRestoreProgressDialog.setProgress(progress);
            mRestoreProgressDialog.setMessage(message);
        }
    });
}

// Tutorial tracking methods
private void initTutorialTracking() {
    mSharedPreferences = mTermuxActivity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
}

private boolean isTutorialOpened() {
    return mSharedPreferences.getBoolean(KEY_TUTORIAL_OPENED, false);
}

private void markTutorialOpened() {
    SharedPreferences.Editor editor = mSharedPreferences.edit();
    editor.putBoolean(KEY_TUTORIAL_OPENED, true);
    editor.apply();
    stopHelpButtonFlashing();
}

private void startHelpButtonFlashing() {
    if (mIsFlashing || mHelpButton == null) return;
    
    mIsFlashing = true;
    final int highlightColor = Color.argb(150, 255, 165, 0); // Orange
    final int normalColor = Color.TRANSPARENT;
    
    mFlashRunnable = new Runnable() {
        boolean highlighted = false;
        
        @Override
        public void run() {
            if (!mIsFlashing || mHelpButton == null) return;
            
            if (highlighted) {
                mHelpButton.setBackgroundColor(normalColor);
            } else {
                mHelpButton.setBackgroundColor(highlightColor);
            }
            highlighted = !highlighted;
            
            mFlashHandler.postDelayed(this, 600); // Flash every 600ms
        }
    };
    
    mFlashHandler.post(mFlashRunnable);
}

private void stopHelpButtonFlashing() {
    mIsFlashing = false;
    if (mFlashHandler != null && mFlashRunnable != null) {
        mFlashHandler.removeCallbacks(mFlashRunnable);
    }
    if (mHelpButton != null) {
        mHelpButton.setBackgroundColor(Color.TRANSPARENT);
    }
}

private void checkTutorialState() {
    // Find the help button in the grid
    for (int i = 0; i < mToolboxGrid.getChildCount(); i++) {
        View child = mToolboxGrid.getChildAt(i);
        if (child instanceof LinearLayout) {
            // Check if this is the help button by looking at its content
            LinearLayout layout = (LinearLayout) child;
            if (layout.getChildCount() >= 2) {
                View secondChild = layout.getChildAt(1);
                if (secondChild instanceof TextView) {
                    TextView textView = (TextView) secondChild;
                    if (textView.getText().toString().equals(mTermuxActivity.getString(R.string.toolbox_help))) {
                        mHelpButton = layout;
                        break;
                    }
                }
            }
        }
    }
    
    // Start flashing if tutorial hasn't been opened
    if (!isTutorialOpened() && mHelpButton != null) {
        startHelpButtonFlashing();
    }
}

private void startTutorial() {
    markTutorialOpened(); // Mark as opened when tutorial starts
    mTutorialStep = 0;
    showTutorialStep();
}


private void showTutorialStep() {
    String[] steps = mTermuxActivity.getResources().getStringArray(R.array.tutorial_steps);
    if (mTutorialStep >= steps.length) {
        mToolboxPopup.setFocusable(true);
        return;
    }

    // Clear previous highlights
    for(int i=0; i<mToolboxGrid.getChildCount(); i++) {
        mToolboxGrid.getChildAt(i).setBackgroundResource(R.drawable.tool_box_background);
    }

    AlertDialog.Builder builder = new AlertDialog.Builder(mTermuxActivity);
builder.setMessage(steps[mTutorialStep])
       .setCancelable(false)
       .setPositiveButton(mTutorialStep < steps.length-1 ? 
           R.string.next : R.string.finish_tutorial, (dialog, which) -> {
           mTutorialStep++;
           if(mTutorialStep >= steps.length) {
               // Final step - dismiss both dialog and toolbox
               mToolboxPopup.dismiss();
           } else {
               // Continue to next step
               showTutorialStep();
           }
       });

    AlertDialog dialog = builder.create();
    
    // Semi-transparent background instead of full transparent
    dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.argb(200, 30, 30, 30))); // Dark gray
    // Position dialog at bottom
    Window window = dialog.getWindow();
    if (window != null) {
        window.setGravity(Gravity.BOTTOM);
        WindowManager.LayoutParams params = window.getAttributes();
        params.width = WindowManager.LayoutParams.MATCH_PARENT;
        params.horizontalMargin = 32;
        params.verticalMargin = 16;
        window.setAttributes(params);
    }
    dialog.show();


    // Custom text styling (from previous answer)
    TextView messageText = dialog.findViewById(android.R.id.message);
    if (messageText != null) {
        messageText.setGravity(Gravity.CENTER);
        messageText.setTextColor(Color.WHITE);
        messageText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        messageText.setPadding(32, 32, 32, 32);
    }

    // Custom button styling
    Button positiveButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
    if (positiveButton != null) {
        positiveButton.setTextColor(Color.parseColor("#BB86FC"));
        positiveButton.setBackgroundResource(R.drawable.tutorial_button_bg);
        positiveButton.setAllCaps(false);
        positiveButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
    }



    // Highlight current button 
    // Update highlightOrder
int[] highlightOrder = {0,1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18}; 
// 
    if(mTutorialStep < highlightOrder.length) {
        View targetButton = mToolboxGrid.getChildAt(highlightOrder[mTutorialStep]);
        targetButton.setBackgroundColor(Color.argb(150, 255, 165, 0)); // Orange overlay
    }
    
}




private void showBlockingView() {
    // Create root container
    RelativeLayout blockingLayout = new RelativeLayout(mTermuxActivity);
    blockingLayout.setLayoutParams(new ViewGroup.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.MATCH_PARENT
    ));
    blockingLayout.setBackgroundColor(Color.parseColor("#80000000")); // Semi-transparent dark
    
    // Make it intercept all touch events
    blockingLayout.setClickable(true);
    blockingLayout.setFocusable(true);
    blockingLayout.setFocusableInTouchMode(true);
    
    // Create loading container
    LinearLayout loadingContainer = new LinearLayout(mTermuxActivity);
    loadingContainer.setOrientation(LinearLayout.VERTICAL);
    loadingContainer.setGravity(Gravity.CENTER);
    
    // Create progress spinner
    ProgressBar progress = new ProgressBar(mTermuxActivity, null, android.R.attr.progressBarStyleLarge);
    progress.setIndeterminate(true);
    
    // Create loading text
    TextView text = new TextView(mTermuxActivity);
    text.setText("⏳Loading...");
    text.setTextColor(Color.WHITE);
    text.setTextSize(18);
    text.setPadding(0, 16, 0, 0);
    
    // Add views
    loadingContainer.addView(progress);
    loadingContainer.addView(text);
    blockingLayout.addView(loadingContainer, new RelativeLayout.LayoutParams(
        ViewGroup.LayoutParams.WRAP_CONTENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ) {
        {
            addRule(RelativeLayout.CENTER_IN_PARENT);
        }
    });
    
    // Add to window
    ViewGroup rootView = (ViewGroup) mTermuxActivity.getWindow().getDecorView();
    rootView.addView(blockingLayout);
    
    mBlockingView = blockingLayout;
}

private void hideBlockingView() {
    if (mBlockingView != null) {
        ViewGroup rootView = (ViewGroup) mBlockingView.getParent();
        if (rootView != null) {
            rootView.removeView(mBlockingView);
        }
        mBlockingView = null;
    }
}


public static String getRealPath(Context context, Uri uri) {
    try (InputStream in = context.getContentResolver().openInputStream(uri)) {
        File tempFile = File.createTempFile("temp_", null, context.getCacheDir());
        try (OutputStream out = new FileOutputStream(tempFile)) {
            byte[] buffer = new byte[1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        }
        return tempFile.getAbsolutePath();
    } catch (Exception e) {
        return null;
    }
}

private String mSelectedWineCommand;
private final String[] wineCommands = {
    "/data/data/com.termux/files/usr/glibc/opt/scripts/xodos_wine2",
    "/data/data/com.termux/files/usr/bin/xbio2"
};


private void saveToStartMenuEntries() {
    File file = new File(TERMUX_HOME_DIR_PATH, ".startMenuEntries");
    try {
        JSONObject data = new JSONObject();
        data.put("version", "1.0");
        data.put("name", "startMenuEntries");
        data.put("currentStartItem", mCurrentCommand);

        JSONArray elementsJSONArray = new JSONArray();
        for (StartEntry.Entry entry : StartEntry.getStartItemList()) {
            JSONObject entryJson = new JSONObject();
            entryJson.put("path", entry.getPath());
            entryJson.put("fileName", entry.getFileName());
            entryJson.put("iconPath", entry.getIconPath());
            entryJson.put("type", entry.getType() != null ? entry.getType() : "file");
            entryJson.put("title", entry.getFileName());
            elementsJSONArray.put(entryJson);
        }
        data.put("elements", elementsJSONArray);
        
             // Make sure parent directory exists
        file.getParentFile().mkdirs();
        FileUtils.writeString(file, data.toString());
    } catch (JSONException e) {
        Toast.makeText(mTermuxActivity, "Failed to save entries: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}

private void loadFromStartMenuEntries() {
    File file = new File(TERMUX_HOME_DIR_PATH, ".startMenuEntries");
    if (!file.exists() || !file.isFile()) {
        return;
    }

    try {
        JSONObject config = new JSONObject(FileUtils.readString(file));
        JSONArray elements = config.getJSONArray("elements");
        
        for (int i = 0; i < elements.length(); i++) {
            JSONObject item = elements.getJSONObject(i);
            StartEntry.Entry entry = new StartEntry.Entry();
            entry.setPath(item.getString("path"));
            entry.setFileName(item.getString("fileName"));
            entry.setIconPath(item.getString("iconPath"));
            entry.setType(item.optString("type", "file"));
            StartEntry.addStartEntry(entry);
            refreshToolboxItems(); 
        }
        
        if (config.has("currentStartItem")) {
            mCurrentCommand = config.getInt("currentStartItem");
        }
    } catch (Exception e) {
        Toast.makeText(mTermuxActivity, "Failed to load entries: " + e.getMessage(), Toast.LENGTH_SHORT).show();
    }
}

private void addCommandButton(String type, String title, String command) {
    LinearLayout btn = createToolboxButton(type, title);
    btn.setOnClickListener(v -> {   
        mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command + "\n");
        mToolboxPopup.dismiss();
    });
    mToolboxGrid.addView(btn);
}

private String determineFileType(FileInfo fileInfo) {
    if (fileInfo == null || fileInfo.getName() == null) return "file";
    
    String fileName = fileInfo.getName().toLowerCase();
    if (fileName.endsWith(".sh")) return "script";
    if (fileName.endsWith(".txt")) return "text";
    if (fileName.endsWith(".py")) return "python";
    if (fileName.endsWith(".deb")) return "package";
    return "file";
}

private LinearLayout createToolboxButton(String type, String title) {
  // Handle null type
    if (type == null) {
        type = "unknown"; // Default fallback
        // Log the error for debugging
        Toast.makeText(mTermuxActivity, "Null type encountered for: " + title, 
        Toast.LENGTH_SHORT).show();
    }

    LinearLayout layout = new LinearLayout(mTermuxActivity);
    int size = ScreenUtils.getScreenWidth(mTermuxActivity) / 5;
    LinearLayout.LayoutParams param = new LinearLayout.LayoutParams(size, size);
    param.setMargins(10, 10, 10, 10);
    layout.setLayoutParams(param);
    layout.setOrientation(LinearLayout.VERTICAL);
    layout.setGravity(Gravity.CENTER);
  //  layout.setBackgroundResource(R.drawable.tool_box_background); // Add background
layout.setBackgroundResource(R.drawable.button_press_effect);
    
    // Add for better interaction:
    layout.setClickable(true);
    layout.setFocusable(true);
    layout.setForeground(mTermuxActivity.getResources()
        .getDrawable(R.drawable.button_press_effect));


    ImageView iv = new ImageView(mTermuxActivity);
    // Set image based on type
// Add weight to image view like original
    int imageSize = (int) (size * 0.6);
    LinearLayout.LayoutParams imageParams = new LinearLayout.LayoutParams(
        imageSize, 
        imageSize
    );
    iv.setLayoutParams(imageParams); // Critical missing line
    iv.setScaleType(ImageView.ScaleType.FIT_CENTER);


iv.setScaleType(ImageView.ScaleType.FIT_CENTER); // Add this for proper scaling
  
      switch(type) {
      case "FM":
    iv.setImageResource(R.drawable.ifm); 
    break;
       case "wid":
    iv.setImageResource(R.drawable.iwid); 
    break;
      case "lnk":
    iv.setImageResource(R.drawable.ilnk); 
          break;
      case "upda":
    iv.setImageResource(R.drawable.iupdate); 
          break;
      case "clean":
    iv.setImageResource(R.drawable.idel_l); 
    break;
       case "wizard":
    iv.setImageResource(R.drawable.iwiz); 
    break;
      case "help":
    iv.setImageResource(R.drawable.ihelp); //
    break;
    case "switch":
    iv.setImageResource(R.drawable.iswitch); // 
    break;
    case "ai":
    iv.setImageResource(R.drawable.iai); // 
    break;
    case "kali":
    iv.setImageResource(R.drawable.ikali); // 
    break;
    case "proot":
    iv.setImageResource(R.drawable.iproot); // 
    break;
    case "wine":
    iv.setImageResource(R.drawable.idel); // 
    break;
    case "xset":
    iv.setImageResource(R.drawable.iset); 
    break;
    case "xgl":
    iv.setImageResource(R.drawable.igl); 
    break;
    case "xbio":
    iv.setImageResource(R.drawable.ibi); 
    break;
    case "xbox":
    iv.setImageResource(R.drawable.ixbox); 
    break;
    case "shortcuts":
    iv.setImageResource(R.drawable.igame); 
    break;
    case "xodos":
    iv.setImageResource(R.drawable.idesk); 
    break;
        case "backup":
    iv.setImageResource(R.drawable.ibackup);
    break;
case "restore":
    iv.setImageResource(R.drawable.irestore);
    break;
    case "add":
        iv.setImageResource(R.drawable.iadd); 
        break;
      // Add new cases from second code block
        case "script":
            iv.setImageResource(R.drawable.iscript);
            break;
        case "executable":
            iv.setImageResource(R.drawable.iexcut);
            break;
        case "short_cut":
            iv.setImageResource(R.drawable.ic_shortcut_click);
            break;
        case "terminal":
            iv.setImageResource(R.drawable.iterm);
            break;
        default:
            iv.setImageResource(R.drawable.iexcut); // Changed to custom default
    }


    
   TextView tv = new TextView(mTermuxActivity);
    // Match original text styling
        tv.setLayoutParams(new LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ));
    
    tv.setText(title);
    tv.setGravity(Gravity.CENTER);
    tv.setTextColor(Color.WHITE);
    tv.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_UNIFORM);
    tv.setAutoSizeTextTypeUniformWithConfiguration(
        8, // Minimum text size (sp)
        14, // Maximum text size (sp)
        1, // Step size (sp)
        TypedValue.COMPLEX_UNIT_SP
    );

    // Ensure text wraps if needed
    tv.setMaxLines(2);
    tv.setEllipsize(TextUtils.TruncateAt.END);
    tv.setPadding(0, 8, 0, 0);
    tv.setEllipsize(TextUtils.TruncateAt.END);
   
  //  tv.setMaxLines(2);
    
tv.setTextColor(Color.parseColor("#FFFFFF")); // Ensure white text
tv.setBackgroundColor(Color.parseColor("#40000000")); // Add subtle background
tv.setTypeface(Typeface.DEFAULT_BOLD); // Improve readability

    layout.addView(iv);
    layout.addView(tv);
    return layout;
}

private LinearLayout createToolboxButton(StartEntry.Entry entry) {
    return createToolboxButton(entry.getType(), entry.getFileName());
}


    private StartEntryClient() {
    }

    public StartEntryClient(TermuxActivity activity, TermuxTerminalSessionActivityClient termuxTerminalSessionActivityClient) {
        this.mTermuxActivity = activity;
        this.mTermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient;
    }

    public void init() {
        // Initialize tutorial tracking
        initTutorialTracking();
    
        // Load from both formats for backward compatibility
        StartEntry.loadStartItems(); // Original .startItemEntries
        loadFromStartMenuEntries(); // New .startMenuEntries
        
        mStartItemEntriesConfig = mTermuxActivity.findViewById(com.termux.x11.R.id.LConfigStartItems);
        mLaunchButton = mTermuxActivity.findViewById(com.termux.x11.R.id.BLaunch_item);
        mLaunchButton.setVisibility(View.GONE); // Hide but keep code
        mSelectConfigButton = mTermuxActivity.findViewById(com.termux.x11.R.id.BAdd_item);
        mAddConfigButton = mTermuxActivity.findViewById(com.termux.x11.R.id.BConfig_item);
        mRemoveConfigButton = mTermuxActivity.findViewById(com.termux.x11.R.id.BRemove_item);
        mLaunchItemSpinner = mTermuxActivity.findViewById(com.termux.x11.R.id.SLaunchItemList);
     //   mLaunchButton.setVisibility(View.VISIBLE);
        setupToolboxPopup();
     
    // Initialize Toolbox button (reusing MenuEntryClient's functionality)
    Button btnToolbox = mTermuxActivity.findViewById(com.termux.x11.R.id.toggle_tool_box2);
btnToolbox.setOnClickListener(v -> {
    // First create/update the popup contents
    refreshToolboxItems();
    
    // recalculate popup width/height for current orientation
int width = ScreenUtils.getScreenWidth(mTermuxActivity);
DisplayMetrics metrics = new DisplayMetrics();
mTermuxActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
int height = (int)(metrics.heightPixels * 0.7);

mToolboxPopup.setWidth(width);
mToolboxPopup.setHeight(height);
    
    // Then show the popup
    int y = 120;
    if (mTermuxActivity.getExtraKeysView().getVisibility() == View.VISIBLE) {
        y += mTermuxActivity.getExtraKeysView().getHeight();
    }
    mToolboxPopup.showAtLocation(
        mTermuxActivity.findViewById(R.id.left_drawer),
        Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM,
        0, y
    );
});

    // ===== End of new section =====
        
        mStartItemEntriesConfig.setVisibility(View.GONE);
        mCurrentCommand = StartEntry.getCurrentStartItemIdx();
        mEntries = StartEntry.getStartItemList();
        if (mCurrentCommand < StartEntry.getStartItemList().size()) {
            mLaunchButton.setText(StartEntry.getStartItemList().get(mCurrentCommand).getFileName());
        }


mToolboxPopup.setOnDismissListener(() -> {
       // StartEntry.saveStartItems();
       saveToStartMenuEntries();
        refreshToolboxItems();
        stopHelpButtonFlashing(); // Stop flashing when popup is dismissed
    });




        mAdapter = new StartEntryArrayAdapter(mTermuxActivity, StartEntry.getStartItemList());
        mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mLaunchItemSpinner.setAdapter(mAdapter);
        mLaunchItemSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                mLaunchButton.setText(mAdapter.getItem(position).getFileName());
                mCurrentCommand = position;
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                mLaunchButton.setText(mTermuxActivity.getResources().getString(com.termux.x11.R.string.launch_button_text));
            }
        });

        mLaunchButton.setOnClickListener((v) -> {
            if(StartEntry.getStartItemList().isEmpty()){
                Toast.makeText(mTermuxActivity, mTermuxActivity.getResources().getString(R.string.select_command_first), Toast.LENGTH_SHORT).show();
                mLaunchButton.setVisibility(View.GONE);
                mStartItemEntriesConfig.setVisibility(View.VISIBLE);
                return;
            }
            if (mCurrentCommand >= StartEntry.getStartItemList().size()) {
                Toast.makeText(mTermuxActivity, mTermuxActivity.getResources().getString(R.string.no_such_file) + ": " + "empty command", Toast.LENGTH_SHORT).show();
                return;
            }
            File file = new File(StartEntry.getStartItemList().get(mCurrentCommand).getPath());
            if (!file.exists()) {
                Toast.makeText(mTermuxActivity, mTermuxActivity.getResources().getString(R.string.no_such_file) + ": " + StartEntry.getStartItemList().get(mCurrentCommand).getFileName(), Toast.LENGTH_SHORT).show();
                return;
            }
            if (!file.canExecute()) {
                Toast.makeText(mTermuxActivity, mTermuxActivity.getResources().getString(R.string.not_executable) + ": " + StartEntry.getStartItemList().get(mCurrentCommand).getFileName(), Toast.LENGTH_SHORT).show();
                return;
            }
            String command = "^c";
            if (StartEntry.getStartItemList().get(mCurrentCommand).getPath().contains(TERMUX_BIN_PREFIX_DIR_PATH)) {
                command = StartEntry.getStartItemList().get(mCurrentCommand).getFileName() + "\n";
            } else {
                command = StartEntry.getStartItemList().get(mCurrentCommand).getPath() + "\n";
            }

            mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
        });
        mLaunchButton.setOnLongClickListener(l -> {
            mLaunchButton.setVisibility(View.GONE);
            mStartItemEntriesConfig.setVisibility(View.VISIBLE);
            return true;
        });
        mSelectConfigButton.setOnClickListener((v) -> {
            if (mLaunchButton.getVisibility() == View.VISIBLE) {
                mLaunchButton.setVisibility(View.GONE);
                mStartItemEntriesConfig.setVisibility(View.VISIBLE);
            } else {
              //  mLaunchButton.setVisibility(View.VISIBLE);
                mStartItemEntriesConfig.setVisibility(View.GONE);
                StartEntry.setCurrentStartItemIdx(mCurrentCommand);
                StartEntry.saveStartItems();
            }
        });

        mFileBrowser = new FileBrowser(mTermuxActivity, this);
        mFileBrowser.init();
        mAddConfigButton.setOnClickListener(v -> {
            mFileBrowser.showFileBrowser(mStartItemEntriesConfig);
        });
        mRemoveConfigButton.setOnClickListener(v -> {
            if(StartEntry.getStartItemList().isEmpty()){
                return;
            }
            StartEntry.deleteStartEntry(StartEntry.getStartItemList().get(mCurrentCommand));
            mAdapter.notifyDataSetChanged();
            StartEntry.saveStartItems();
        });
    }


///////////////////

private void launchWineFilePicker() {
    // Use same simple file picker as restore
    Intent intent = new Intent(Intent.ACTION_GET_CONTENT)
        .setType("*/*")
        .addCategory(Intent.CATEGORY_OPENABLE);
    mTermuxActivity.startActivityForResult(intent, TermuxActivity.FILE_REQUEST_WINE_CODE);
}

public void handleWineFile(Uri uri) {
    try {
        if (mSelectedWineCommand == null) {
            throw new Exception("No Wine environment selected");
        }

        // Get direct path using restore's method
        String path = FilePathUtils.getPath(mTermuxActivity, uri);
        if (path == null) {
            throw new Exception("Could not resolve file path");
        }

        // Validate file extension
        String ext = path.substring(path.lastIndexOf(".")).toLowerCase();
     //   if (!ext.equals(".exe")) {
       //     throw new Exception("Only .exe files are supported");
      //  }

        // Build and execute command
        String command = mSelectedWineCommand + " \"" + path + "\"\n";
        mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
        mToolboxPopup.dismiss();

    } catch (Exception e) {
        Toast.makeText(mTermuxActivity, "Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
    }
}

// Update 


private void launchRestoreFilePicker() {
Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)            // <-- use OPEN_DOCUMENT
    .setType("*/*")
    .addCategory(Intent.CATEGORY_OPENABLE)
    // grant temporary read (and write) permission...
    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    // ...and ask Android to let us persist that grant across reboots
    .addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

mTermuxActivity.startActivityForResult(intent, TermuxActivity.FILE_REQUEST_BACKUP_CODE);
    }


private void setupToolboxPopup() {
    // 1. Create main popup window
    mToolboxPopup = new PopupWindow(mTermuxActivity);
    mToolboxPopup.setBackgroundDrawable(mTermuxActivity.getDrawable(R.drawable.tool_box_background));
    // 2. Create grid for items
    mToolboxGrid = new GridLayout(mTermuxActivity);
    mToolboxGrid.setColumnCount(4);
    int padding = (int) (ScreenUtils.getScreenWidth(mTermuxActivity) * 0.05f);
    mToolboxGrid.setPadding(padding, padding/2, padding, padding/2);
    // 3. Create container layout
    ScrollView scrollView = new ScrollView(mTermuxActivity);
    LinearLayout mainContainer = new LinearLayout(mTermuxActivity);
    mainContainer.setOrientation(LinearLayout.VERTICAL);
    
    // 4. Add close button
    ImageButton closeBtn = new ImageButton(mTermuxActivity);
    closeBtn.setImageResource(R.drawable.ic_close);
    closeBtn.setOnClickListener(v -> mToolboxPopup.dismiss());
    
   
    // 5. Assemble components
    mainContainer.addView(closeBtn);
    scrollView.addView(mToolboxGrid);
    mainContainer.addView(scrollView);
    mToolboxPopup.setContentView(mainContainer);
    
    // Set dimensions
int width = ScreenUtils.getScreenWidth(mTermuxActivity);
    mToolboxPopup.setWidth(width);
    mToolboxPopup.setHeight(width * 3/3);
    
        // Set dimensions
    DisplayMetrics metrics = new DisplayMetrics();
    mTermuxActivity.getWindowManager().getDefaultDisplay().getMetrics(metrics);
mToolboxPopup.setWidth(width);
    int height = (int)(metrics.heightPixels * 0.7);
    
    mToolboxPopup.setWidth(width);
    mToolboxPopup.setHeight(height); // 
}

private void refreshToolboxItems() {
if (mToolboxGrid == null) {
        setupToolboxPopup(); // Ensure initialization
        return;
    }
    mToolboxGrid.removeAllViews();
    
    // Add existing StartEntry items
for (StartEntry.Entry entry : StartEntry.getStartItemList()) {
        // Validate entry before use
        if (entry.getType() == null || entry.getFileName() == null) {
           // Toast.makeText(mTermuxActivity, "Skipping invalid entry", 
        // Toast.LENGTH_SHORT).show();
            continue;
        }
        
        LinearLayout btn = createToolboxButton(entry);
        
     // Click to execute
        btn.setOnClickListener(v -> executeEntry(entry));
        
        // Long press to remove
        btn.setOnLongClickListener(v -> {
            new AlertDialog.Builder(mTermuxActivity)
                .setTitle("Remove Shortcut")
                .setMessage("Delete " + entry.getFileName() + "?")
                .setPositiveButton("Yes", (dialog, which) -> {
                StartEntry.deleteStartEntry(entry);
                 // Save to both formats after deletion
            StartEntry.saveStartItems();
            saveToStartMenuEntries();

                    //StartEntry.saveStartItems();
                    refreshToolboxItems(); // Refresh grid
                    mAdapter.notifyDataSetChanged(); // Refresh spinner
                })
                .setNegativeButton("No", null)
                .show();
            return true;
        });
        
        mToolboxGrid.addView(btn);
    }    
    ////////////////////////////
    // Add system buttons after user entries
    addSystemButtons();
    
    // Check tutorial state after adding all buttons
    checkTutorialState();
}

private void addSystemButtons() {
    if (mToolboxGrid == null) {
        setupToolboxPopup(); // Reinitialize if needed
    }
     

// Xodos Button
// Modified XoDos button with wizard launch
LinearLayout xodosBtn = createToolboxButton("xodos", mTermuxActivity.getString(R.string.toolbox_xodos));
xodosBtn.setOnClickListener(v -> {
    // Immediate feedback
    showBlockingView();
    mToolboxPopup.dismiss();
        mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("/data/data/com.termux/files/usr/bin/xodos\n");
        // Start configuration wizard
    // Delay before showing wizard
    mHandler.postDelayed(() -> {
        hideBlockingView();

       // new XoDosWizard(mTermuxActivity).start();
    }, 3000); // 2-second delay
});
mToolboxGrid.addView(xodosBtn);

// Proot Button
LinearLayout prootBtn = createToolboxButton("proot", mTermuxActivity.getString(R.string.toolbox_proot));
prootBtn.setOnClickListener(v -> {
    new AlertDialog.Builder(mTermuxActivity)
        .setTitle("Proot Options")
        .setItems(new CharSequence[]{"With Root", "Without Root"}, (dialog, which) -> {
            String command = (which == 0) 
                ? "/data/data/com.termux/files/usr/bin/xodxd\n" 
                : "/data/data/com.termux/files/usr/bin/xodxdu\n";
            showBlockingView();
            mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
            mToolboxPopup.dismiss();
            mHandler.postDelayed(() -> hideBlockingView(), 3000);
        })
        .setNegativeButton("Cancel", null)
        .show();
});
mToolboxGrid.addView(prootBtn);




// Kali Button
LinearLayout kaliBtn = createToolboxButton("kali", mTermuxActivity.getString(R.string.toolbox_kali));
kaliBtn.setOnClickListener(v -> {
    new AlertDialog.Builder(mTermuxActivity)
        .setTitle("Kali Options")
        .setItems(new CharSequence[]{"With Root", "Without Root"}, (dialog, which) -> {
            String command = (which == 0) 
                ? "/data/data/com.termux/files/usr/bin/kalir\n" 
                : "/data/data/com.termux/files/usr/bin/kaliu\n";
            showBlockingView();
            mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
            mToolboxPopup.dismiss();
            mHandler.postDelayed(() -> hideBlockingView(), 4000);
        })
        .setNegativeButton("Cancel", null)
        .show();
});
mToolboxGrid.addView(kaliBtn);

// Xgl Button (Wine Glibc) - FIXED
LinearLayout xglBtn = createToolboxButton("xgl", mTermuxActivity.getString(R.string.toolbox_wine_glibc));
xglBtn.setOnClickListener(v -> {
    File glibcPath = new File("/data/data/com.termux/files/usr/glibc");
    if (!glibcPath.exists()) {
        new AlertDialog.Builder(mTermuxActivity)
            .setMessage(R.string.wine_glibc_missing)
            .setPositiveButton(android.R.string.ok, null)
            .show();
        return;
    }
    showBlockingView();
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("/data/data/com.termux/files/usr/bin/xodxx\n");
    mToolboxPopup.dismiss();
    mHandler.postDelayed(() -> hideBlockingView(), 4000);
}); // Removed duplicate code block
mToolboxGrid.addView(xglBtn);

// Xbio Button (Wine Bionic) - FIXED
LinearLayout xbioBtn = createToolboxButton("xbio", mTermuxActivity.getString(R.string.toolbox_wine_bionic));
xbioBtn.setOnClickListener(v -> {
    File bionicPath = new File("/data/data/com.termux/files/usr/opt/wine");
    if (!bionicPath.exists()) {
        new AlertDialog.Builder(mTermuxActivity)
            .setMessage(R.string.wine_bionic_missing)
            .setPositiveButton(android.R.string.ok, null)
            .show();
        return;
    }
    showBlockingView();
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("/data/data/com.termux/files/usr/bin/xodxx2\n");
    mToolboxPopup.dismiss();
    mHandler.postDelayed(() -> hideBlockingView(), 4000);
}); // Removed duplicate code block
mToolboxGrid.addView(xbioBtn);

LinearLayout wineBtn = createToolboxButton("wine", mTermuxActivity.getString(R.string.toolbox_wine));

wineBtn.setOnClickListener(v -> {
    new AlertDialog.Builder(mTermuxActivity)
        .setTitle(mTermuxActivity.getString(R.string.choose_wine_env))
        .setItems(new CharSequence[]{
                mTermuxActivity.getString(R.string.wine_glibc),
                mTermuxActivity.getString(R.string.wine_bionic)
            }, (dialog, which) -> {

                String pathToCheck = (which == 0)
                        ? "/data/data/com.termux/files/usr/glibc"
                        : "/data/data/com.termux/files/usr/opt/wine";

                File wineDir = new File(pathToCheck);
                if (!wineDir.exists()) {
                    int messageRes = (which == 0)
                            ? R.string.wine_glibc_missing
                            : R.string.wine_bionic_missing;

                    new AlertDialog.Builder(mTermuxActivity)
                        .setMessage(messageRes)
                        .setPositiveButton(android.R.string.ok, null)
                        .show();
                    return;
                }

                new AlertDialog.Builder(mTermuxActivity)
                    .setTitle(mTermuxActivity.getString(R.string.confirm_delete))
                    .setMessage(mTermuxActivity.getString(R.string.delete_wine_prefix_warning))
                    .setPositiveButton(android.R.string.ok, (d, w) -> {
                        showBlockingView();

                        if (which == 0) {
                            // --- GLIBC WINE ---
                            loadWinePathConf();
                            if (winePrefixFromConf != null && !winePrefixFromConf.isEmpty()) {
                                runShellDeleteCommand(winePrefixFromConf);
                            } else {
                                Toast.makeText(mTermuxActivity,
                                    mTermuxActivity.getString(R.string.error_no_wineprefix_in_conf),
                                    Toast.LENGTH_LONG).show();
                            }
                        } else {
                            // --- BIONIC WINE ---
                            runShellDeleteCommand("/data/data/com.termux/files/home/.wine");
                        }

                        mHandler.postDelayed(this::hideBlockingView, 2000);
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
            })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
});

mToolboxGrid.addView(wineBtn);

// FM button
LinearLayout FMBtn = createToolboxButton("FM", mTermuxActivity.getString(R.string.toolbox_fm));
FMBtn.setOnClickListener(v -> {
    showBlockingView();
    mToolboxPopup.dismiss();

    mHandler.postDelayed(() -> {
        new XoDosFileManager(mTermuxActivity).start();
        hideBlockingView();
    }, 1000);
});
mToolboxGrid.addView(FMBtn);

// wid button
LinearLayout widBtn = createToolboxButton("wid", "termux-widget");

widBtn.setOnClickListener(v -> {
   // showBlockingView();
    mToolboxPopup.dismiss();

    mTermuxActivity.runOnUiThread(() -> {
        AlertDialog.Builder builder = new AlertDialog.Builder(mTermuxActivity);
        builder.setTitle("Choose an option")
               .setItems(new CharSequence[]{"Widgets", "Settings"}, (dialog, which) -> {
                   Intent intent = null;
                   if (which == 0) {
                       // "Widgets" selected
                       intent = new Intent(mTermuxActivity, com.termux.widget.TermuxCreateShortcutActivity.class);
                   } else if (which == 1) {
                       // "Settings" selected
                       intent = new Intent(mTermuxActivity, com.termux.widget.activities.TermuxWidgetMainActivity.class);
                   }

                   if (intent != null) {
                       mTermuxActivity.startActivity(intent);
                   }

                   mHandler.postDelayed(this::hideBlockingView, 2000);
               });

        builder.setOnCancelListener(dialog -> {
            // If user cancels dialog
            //hideBlockingView();
        });

        builder.show();
    });
});
mToolboxGrid.addView(widBtn);

// Settings (Xset) Button
LinearLayout settingsBtn = createToolboxButton("xset", mTermuxActivity.getString(R.string.toolbox_settings));
settingsBtn.setOnClickListener(v -> {
    showBlockingView();
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("/data/data/com.termux/files/usr/bin/xmnu2\n");
    mToolboxPopup.dismiss();
    mHandler.postDelayed(() -> hideBlockingView(), 4000);
});
mToolboxGrid.addView(settingsBtn);

// Ai Button
addCommandButton("ai", mTermuxActivity.getString(R.string.toolbox_ai), "/data/data/com.termux/files/usr/bin/xodai");

// Restore Button
// Update the restore button onClickListener in addSystemButtons()
LinearLayout restoreBtn = createToolboxButton("restore", mTermuxActivity.getString(R.string.toolbox_restore));
restoreBtn.setOnClickListener(v -> {
    if(mRestoreInProgress) {
        Toast.makeText(mTermuxActivity, " already in progress", Toast.LENGTH_SHORT).show();
        return;
    }
    
    showRestoreProgressDialog();
    launchRestoreFilePicker();
    mToolboxPopup.dismiss();
});
mToolboxGrid.addView(restoreBtn);

// Backup Button
LinearLayout backupBtn = createToolboxButton("backup", mTermuxActivity.getString(R.string.toolbox_backup));
backupBtn.setOnClickListener(v -> {
    showBlockingView();
    Toast.makeText(mTermuxActivity, " backup system to phone,,", Toast.LENGTH_SHORT).show();
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("tar -Jcf /sdcard/xodos-backup.tar.xz -C /data/data/com.termux/files ./home ./usr\n");
    mToolboxPopup.dismiss();
    mHandler.postDelayed(() -> hideBlockingView(), 5000);
});
mToolboxGrid.addView(backupBtn);

// wizard Button
// addCommandButton
LinearLayout wizardBtn = createToolboxButton("wizard", mTermuxActivity.getString(R.string.toolbox_wizard));
wizardBtn.setOnClickListener(v -> {
    // Immediate feedback
    showBlockingView();
    mToolboxPopup.dismiss();

    // Delay before showing wizard
    mHandler.postDelayed(() -> {
            new XoDosWizard(mTermuxActivity).start();
        hideBlockingView();
     // mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("/data/data/com.termux/files/usr/bin/xodos\n");
        // Start configuration wizard

    }, 1000); // 2-second delay
});
mToolboxGrid.addView(wizardBtn);

//shortcuts button
LinearLayout shortcutsBtn = createToolboxButton("shortcuts", mTermuxActivity.getString(R.string.toolbox_shortcuts));
shortcutsBtn.setOnClickListener(v -> {
    showBlockingView();
    mToolboxPopup.dismiss();
    mHandler.postDelayed(() -> {
        new XoDosShortcuts(mTermuxActivity).start();
        hideBlockingView();
    }, 1000);
});
mToolboxGrid.addView(shortcutsBtn);

// Switch Button
LinearLayout switchBtn = createToolboxButton("switch", mTermuxActivity.getString(R.string.toolbox_switch));
switchBtn.setOnClickListener(v -> {
    showBlockingView();
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write("/data/data/com.termux/files/usr/bin/switch\n");
    mToolboxPopup.dismiss();
    mHandler.postDelayed(() -> hideBlockingView(), 6000);
});
mToolboxGrid.addView(switchBtn);
    
    // Add Button (special case)
    LinearLayout addBtn = createToolboxButton("add", "Add");
    addBtn.setOnClickListener(v -> {
        mFileBrowser.showFileBrowser(mToolboxGrid);
        // Don't dismiss popup - keep open for selection
    });
    mToolboxGrid.addView(addBtn);
  
  // update button 
// Add this inside the addSystemButtons() method
LinearLayout updateBtn = createToolboxButton("upda", mTermuxActivity.getString(R.string.toolbox_update));
updateBtn.setOnClickListener(v -> {
    // Show confirmation dialog
    new AlertDialog.Builder(mTermuxActivity)
        .setTitle(mTermuxActivity.getString(R.string.update_confirmation_title))
        .setMessage(mTermuxActivity.getString(R.string.update_confirmation_message))
        .setPositiveButton(mTermuxActivity.getString(R.string.button_proceed), (dialog, which) -> {
            // Start the installation process
            showBlockingView();
            mToolboxPopup.dismiss();
            
            // Show progress dialog
            mRestoreProgressDialog = new ProgressDialog(mTermuxActivity);
            mRestoreProgressDialog.setTitle(mTermuxActivity.getString(R.string.install_xodos_title));
            mRestoreProgressDialog.setMessage(mTermuxActivity.getString(R.string.install_preparing));
            mRestoreProgressDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mRestoreProgressDialog.setMax(100);
            mRestoreProgressDialog.setCancelable(false);
            mRestoreProgressDialog.show();
            
            // Run installation in background thread
            new Thread(() -> {
                Process process = null;
                File outFile = null;
                File scriptFile = null;
                
                try {
                    // Phase 1: Copy xodos.tar.xz from assets (0-50%)
                    mHandler.post(() -> {
                        mRestoreProgressDialog.setMessage(
                            mTermuxActivity.getString(R.string.install_copying));
                        mRestoreProgressDialog.setProgress(0);
                    });
                    
                    AssetManager assetManager = mTermuxActivity.getAssets();
                    InputStream in = assetManager.open("xodos.tar.xz");
                    outFile = new File(mTermuxActivity.getFilesDir(), "xodos.tar.xz");
                    FileOutputStream out = new FileOutputStream(outFile);
                    
                    // Get total file size for progress
                    long totalBytes = assetManager.openFd("xodos.tar.xz").getLength();
                    long copiedBytes = 0;
                    byte[] buffer = new byte[1024 * 128]; // 128KB buffer
                    
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        out.write(buffer, 0, bytesRead);
                        copiedBytes += bytesRead;
                        
                        // Update progress (first 50%)
                        int progress = (int) ((copiedBytes * 50) / totalBytes);
                        final int finalProgress = progress;
                        mHandler.post(() -> {
                            mRestoreProgressDialog.setProgress(finalProgress);
                            mRestoreProgressDialog.setMessage(
                                mTermuxActivity.getString(R.string.install_copying) + 
                                " " + finalProgress + "%");
                        });
                    }
                    
                    out.close();
                    in.close();
                    
                    // Copy fix script and make it executable
                    InputStream scriptIn = assetManager.open("fix");
                    scriptFile = new File(mTermuxActivity.getFilesDir(), "fix");
                    FileOutputStream scriptOut = new FileOutputStream(scriptFile);
                    
                    while ((bytesRead = scriptIn.read(buffer)) != -1) {
                        scriptOut.write(buffer, 0, bytesRead);
                    }
                    
                    scriptOut.close();
                    scriptIn.close();
                    scriptFile.setExecutable(true);
                    
                    // Phase 2: Extract with Tar (50-100%)
                    mHandler.post(() -> {
                        mRestoreProgressDialog.setProgress(50);
                        mRestoreProgressDialog.setMessage(
                            mTermuxActivity.getString(R.string.install_extracting));
                    });
                    
                    // Calculate dynamic checkpoint interval
                    int bytesPerRecord = 512; // Tar's block size
                    int targetCheckpoints = 200; // Aim for 200 checkpoints
                    long totalRecords = (totalBytes + bytesPerRecord - 1) / bytesPerRecord;
                    int recordsPerCheckpoint = Math.max(1, (int) (totalRecords / targetCheckpoints));
                    
                    ProcessBuilder processBuilder = new ProcessBuilder(
                        "sh", "-c",
                        "tar -xf " + outFile.getAbsolutePath() +
                        " -C " + TERMUX_FILES_DIR_PATH +
                        " --preserve-permissions " +
                        "--warning=no-file-ignored " +
                        "--checkpoint=" + recordsPerCheckpoint + 
                        " --checkpoint-action=echo=CHECKPOINT " +
                        "--totals 2>&1"
                    );
                    
                    // Set environment
                    Map<String, String> env = processBuilder.environment();
                    env.put("PATH", TERMUX_BIN_PREFIX_DIR_PATH + ":/system/bin");
                    env.put("LD_LIBRARY_PATH", TERMUX_BIN_PREFIX_DIR_PATH.replace("bin", "lib"));
                    
                    // Start process
                    process = processBuilder.start();
                    
                    // Monitor progress
                    BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                    
                    int checkpointCount = 0;
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("CHECKPOINT")) {
                            checkpointCount++;
                            int progress = 50 + (int) ((checkpointCount * 50.0) / 50);
                            progress = Math.min(progress, 99);
                            final int finalProgress = progress;
                            mHandler.post(() -> {
                                mRestoreProgressDialog.setProgress(finalProgress);
                                mRestoreProgressDialog.setMessage(
                                    mTermuxActivity.getString(R.string.install_extracting) + 
                                    " " + finalProgress + "%");
                            });
                        }
                    }
                    
                    int exitCode = process.waitFor();
                    if (exitCode != 0) {
                        throw new IOException("Extraction failed (code " + exitCode + ")");
                    }
                    
                    // Phase 3: Run fix after extraction
                    mHandler.post(() -> {
                        mRestoreProgressDialog.setProgress(99);
                        mRestoreProgressDialog.setMessage(
                            mTermuxActivity.getString(R.string.install_finalizing));
                    });
                    
                    process = new ProcessBuilder("sh", scriptFile.getAbsolutePath()).start();
                    int scriptExitCode = process.waitFor();
                    if (scriptExitCode != 0) {
                        throw new IOException("Fix script failed with code: " + scriptExitCode);
                    }
                    
                    // Success
                    mHandler.post(() -> {
                        mRestoreProgressDialog.setProgress(100);
                        mRestoreProgressDialog.setMessage(
                            mTermuxActivity.getString(R.string.install_complete));
                        
                        new Handler().postDelayed(() -> {
                            if (mRestoreProgressDialog.isShowing()) {
                                mRestoreProgressDialog.dismiss();
                            }
                            hideBlockingView();
                            
                            // Exit app to complete installation
                            mTermuxActivity.finishAffinity();
                            System.exit(0);
                        }, 2000);
                    });
                    
                } catch (Exception e) {
                    mHandler.post(() -> {
                        new AlertDialog.Builder(mTermuxActivity)
                            .setTitle(mTermuxActivity.getString(R.string.install_error))
                            .setMessage(mTermuxActivity.getString(
                                R.string.install_failed, e.getMessage()))
                            .setPositiveButton(android.R.string.ok, null)
                            .show();
                        
                        if (mRestoreProgressDialog != null && mRestoreProgressDialog.isShowing()) {
                            mRestoreProgressDialog.dismiss();
                        }
                        hideBlockingView();
                    });
                } finally {
                                        // Cleanup on error
                        outFile.delete();
                        scriptFile.delete();
                    // Cleanup files in ALL cases (success or failure)
                    if (outFile != null && outFile.exists()) {
                        if (!outFile.delete()) {
                            Log.e("StartEntryClient", "Failed to delete xodos.tar.xz");
                        }
                    }
                    if (scriptFile != null && scriptFile.exists()) {
                        if (!scriptFile.delete()) {
                            Log.e("StartEntryClient", "Failed to delete fix script");
                        }
                    }
                    if (process != null) {
                        process.destroy();
                    }
                }
            }).start();
        })
        .setNegativeButton(android.R.string.cancel, null)
        .setIcon(android.R.drawable.ic_dialog_alert)
        .show();
});
mToolboxGrid.addView(updateBtn);

// Add Clean Wine Button
    LinearLayout cleanWineBtn = createToolboxButton("clean", mTermuxActivity.getString(R.string.toolbox_clean_wine));
    cleanWineBtn.setOnClickListener(v -> showWineCleanDialog());
    mToolboxGrid.addView(cleanWineBtn);

      // Help Button - Updated with tutorial tracking
mHelpButton = createToolboxButton("help", mTermuxActivity.getString(R.string.toolbox_help));
mHelpButton.setOnClickListener(v -> {
    markTutorialOpened(); // Mark as opened when clicked
    startTutorial();
});
mToolboxGrid.addView(mHelpButton);
}
////////////////
    
private void showWineCleanDialog() {
mToolboxPopup.dismiss();
    new AlertDialog.Builder(mTermuxActivity)
        .setTitle("What to remove?🗑️")
        .setItems(new String[]{"Wine🍷", "Kali🐉", "Proot🐧"}, (dialog, which) -> {
            switch(which) {
                case 0: // Wine
                    showWineSubOptions();
                    break;
                case 1: // Kali
                    cleanKaliEnvironment();
                    break;
                case 2: // Proot
                    showProotDistroSelection();
                    break;
            }
        })
        .setNegativeButton(android.R.string.cancel, null)
        .show();
}

private void cleanKaliEnvironment() {
    new Thread(() -> {
        try {
            String command = "rm -rf $HOME/chroot\n";
            mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
            
            mHandler.post(() -> 
                Toast.makeText(mTermuxActivity,
                    "Kali chroot directory removed✅",
                    Toast.LENGTH_SHORT).show()
            );
        } catch (Exception e) {
            mHandler.post(() -> 
                Toast.makeText(mTermuxActivity,
                    "Error cleaning Kali❌: " + e.getMessage(),
                    Toast.LENGTH_LONG).show()
            );
        }
    }).start();
}

private void showProotDistroSelection() {
    new Thread(() -> {
        File prootRoot = new File(TERMUX_FILES_DIR_PATH + "/usr/var/lib/proot-distro/installed-rootfs");
        final File[] distros = prootRoot.listFiles(File::isDirectory);

        mTermuxActivity.runOnUiThread(() -> {
            if (distros == null || distros.length == 0) {
                Toast.makeText(mTermuxActivity, 
                    "No proot distributions installed", 
                    Toast.LENGTH_SHORT).show();
                return;
            }

            String[] distroNames = new String[distros.length];
            for (int i = 0; i < distros.length; i++) {
                distroNames[i] = distros[i].getName();
            }

            new AlertDialog.Builder(mTermuxActivity)
                .setTitle("Remove🗑️Proot🐧Distribution")
                .setItems(distroNames, (dialog, which) -> {
                    deleteProotDistro(distros[which]);
                })
                .setNegativeButton("Cancel", null)
                .show();
        });
    }).start();
}

private void deleteProotDistro(File distroDir) {
    new Thread(() -> {
        try {
            String deleteCommand = "rm -rf \"" + distroDir.getAbsolutePath() + "\"\n";
            mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(deleteCommand);

            mHandler.post(() -> 
                Toast.makeText(mTermuxActivity,
                    "Removed✅: " + distroDir.getName(),
                    Toast.LENGTH_SHORT).show()
            );
        } catch (Exception e) {
            mHandler.post(() -> 
                Toast.makeText(mTermuxActivity,
                    "Error removing❌ " + distroDir.getName() + ": " + e.getMessage(),
                    Toast.LENGTH_LONG).show()
            );
        }
    }).start();
}

// Keep existing Wine cleanup dialog
private void showWineSubOptions() {
    new AlertDialog.Builder(mTermuxActivity)
        .setTitle(R.string.clean_wine_title)
        .setMessage(R.string.clean_wine_warning)
        .setPositiveButton(R.string.delete_glibc, (d, w) -> cleanWineEnvironment(true))
        .setNegativeButton(R.string.delete_bionic, (d, w) -> cleanWineEnvironment(false))
        .show();
}

private void cleanWineEnvironment(boolean isGlibc) {
    new Thread(() -> {
        mTermuxActivity.runOnUiThread(() -> showBlockingView()); //
        try {
            String[] commands;
            String prefix = "/data/data/com.termux/files/usr"; // Replace

            if (isGlibc) {
                // Check if $PREFIX/opt/wine exists
                String checkWineCmd = "[ -d \"" + prefix + "/opt/wine\" ] && echo exists || echo missing";
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", checkWineCmd});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine().trim();
                reader.close();

                if ("exists".equals(result)) {
                    // Backup and restore Glibc libs
                    commands = new String[]{
                         "mkdir -p " + prefix + "/glibc2/opt/libs",
                         "mkdir -p " + prefix + "/glibc2/opt/prefix",
                        "mv " + prefix + "/glibc/opt/libs/d3d " + prefix + "/glibc2/opt/libs/d3d",
                        "mv " + prefix + "/glibc/opt/prefix/d3d " + prefix + "/glibc2/opt/prefix/d3d",                    
                         "rm -rf " + prefix + "/glibc",
                          "mkdir -p " + prefix + "/glibc/opt/libs",
                         "mkdir -p " + prefix + "/glibc/opt/prefix",
                        "mv " + prefix + "/glibc2/opt/libs/d3d " + prefix + "/glibc/opt/libs/d3d",
                        "mv " + prefix + "/glibc2/opt/prefix/d3d " + prefix + "/glibc/opt/prefix/d3d",                                           
                        "rm -rf " + prefix + "/glibc2"
                    };
                } else {
                    // Just remove Glibc contents
                    commands = new String[]{
                        "rm -rf " + prefix + "/glibc"
                    };
                }

            } else {
            // Check if $PREFIX/Glibc/bin exists
                String checkWineCmd = "[ -d \"" + prefix + "/glibc/bin\" ] && echo exists || echo missing";
                Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", checkWineCmd});
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String result = reader.readLine().trim();
                reader.close();

                if ("exists".equals(result)) {
                    // Remove wine environment
                commands = new String[]{
                    "rm -rf " + prefix + "/opt/wine",
                    "rm -rf " + prefix + "/drivers/25",
                    "rm -rf $HOME/.wine"
                };
                } else {
                    // Just remove Glibc contents
                    // Remove wine environment
                commands = new String[]{
                    "rm -rf " + prefix + "/opt/wine",
                    "rm -rf " + prefix + "/drivers/25",
                    "rm -rf $HOME/.wine",
                    "rm -rf " + prefix + "/glibc"
                };
                }
                
            }

            for (String cmd : commands) {
                mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast()
                    .write(cmd + "\n");
                Thread.sleep(500);
            }

            mHandler.post(() ->
                Toast.makeText(
                    mTermuxActivity,
                    R.string.cleanup_completed,
                    Toast.LENGTH_SHORT
                ).show()
            );
            mHandler.postDelayed(() -> 
                mTermuxActivity.runOnUiThread(() -> hideBlockingView()), // Ensure UI update on main thread
                1000
            );

        } catch (Exception e) {
        mTermuxActivity.runOnUiThread(() -> hideBlockingView()); //
            mHandler.post(() ->
                Toast.makeText(
                    mTermuxActivity,
                    mTermuxActivity.getString(R.string.cleanup_error, e.getMessage()),
                    Toast.LENGTH_LONG
                ).show()
            );
        }
    }).start();
}


private void executeEntry(StartEntry.Entry entry) {
    // Reuse existing launch logic from mLaunchButton's onClickListener
    String command = entry.getPath() + "\n";
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
    mToolboxPopup.dismiss();
}

private void executeBackupCommand() {
    String command = "tar -Jcf /sdcard/xodos-backup.tar.xz -C /data/data/com.termux/files ./home ./usr \n";
    File storageDir = new File(mTermuxActivity.getFilesDir(), "home/storage");
    if (!storageDir.exists()) {
        command = "termux-setup-storage; sleep 5; " + command;
    }
    mTermuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast().write(command);
}

///////////

    public void addStartEntry(FileInfo fileInfo) {
        StartEntry.Entry entry = new StartEntry.Entry();
           // Set the type based on file extension (example logic)
               // Ensure type is never null
    String type = determineFileType(fileInfo); // Your existing logic
    entry.setType(type != null ? type : "file"); // Fallback
    
    
    
          
          ////
   
        entry.setIconPath("default");
        entry.setFileName(fileInfo.getName());
        entry.setPath(fileInfo.getPath());
     
        boolean added = StartEntry.addStartEntry(entry);
        StartEntry.setCurrentStartItemIdx(mCurrentCommand);
        

        if (added) {
            mAdapter.notifyDataSetChanged();
            StartEntry.saveStartItems();  // Original format
        saveToStartMenuEntries();    // New format
            refreshToolboxItems(); //
            mLaunchItemSpinner.setSelection(mAdapter.getCount() - 1);
        }
    }

    @Override
    public void onFileSelected(FileInfo fileInfo) {
        addStartEntry(fileInfo);
        mFileBrowser.hideFileBrowser();
            // Keep toolbox popup open after selection
    refreshToolboxItems();
    mToolboxPopup.showAtLocation(
        mTermuxActivity.findViewById(R.id.left_drawer),
        Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM, 0, 120
    );
    }
    
    private void loadWinePathConf() {
    File confFile = new File(USR_PREFIX + "/glibc/opt/conf/wine_path.conf");
    if (!confFile.exists()) return;

    try (BufferedReader br = new BufferedReader(new FileReader(confFile))) {
        String line;
        while ((line = br.readLine()) != null) {
            if (line.startsWith("export WINEPREFIX=")) {
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    winePrefixFromConf = parts[1].replace("\"", "").trim();
                }
            }
        }
    } catch (IOException e) {
        Toast.makeText(mTermuxActivity,
            "Error reading wine_path.conf: " + e.getMessage(),
            Toast.LENGTH_LONG).show();
    }
}

private void runShellDeleteCommand(String targetPath) {
    try {
        Process process = new ProcessBuilder("sh", "-c", "rm -rf \"" + targetPath + "\"").start();
        int exitCode = process.waitFor();

        if (exitCode == 0) {
            Toast.makeText(mTermuxActivity,
                "Wine prefix deleted successfully ✅",
                Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(mTermuxActivity,
                "Failed to delete prefix (exit " + exitCode + ")",
                Toast.LENGTH_LONG).show();
        }
    } catch (Exception e) {
        Toast.makeText(mTermuxActivity,
            "Error deleting prefix: " + e.getMessage(),
            Toast.LENGTH_LONG).show();
    }
}

// Add cleanup method to prevent memory leaks
public void cleanup() {
    stopHelpButtonFlashing();
    if (mFlashHandler != null) {
        mFlashHandler.removeCallbacksAndMessages(null);
    }
}
}