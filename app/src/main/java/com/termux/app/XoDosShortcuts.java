package com.termux.app;

import android.graphics.Color;
import android.graphics.Bitmap;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.*;
import android.widget.Toast;
import android.text.TextUtils;
import android.view.Gravity;

import com.termux.R;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Add these imports for terminal session
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;

public class XoDosShortcuts {

    private final Context mContext;
    private final String termuxHome = "/data/data/com.termux/files/home";
    private final File desktopDir = new File(termuxHome + "/Desktop/shortcuts");
    private final File desktopDir2 = new File(termuxHome + "/Desktop");
    private final File iconDir = new File(termuxHome + "/ico");
    private static final String PREFS_NAME = "com.termux_preferences";
    private static final String USR_PREFIX = "/data/data/com.termux/files/usr";
    private GridLayout currentGrid;
    private AlertDialog currentDialog;
    private ProgressDialog extractionProgressDialog;
    private Handler mHandler = new Handler();

    public XoDosShortcuts(Context context) {
        this.mContext = context;
        if (!iconDir.exists()) iconDir.mkdirs();
        if (!desktopDir.exists()) desktopDir.mkdirs();
    }

    public void start() {
        showShortcutGrid();
    }

    /** Show grid of .desktop shortcuts with LNK support **/
    private void showShortcutGrid() {
        View view = LayoutInflater.from(mContext).inflate(R.layout.shortcut_grid_layout, null);
        GridLayout grid = view.findViewById(R.id.shortcutGrid);
        this.currentGrid = grid;

        // Create and show the dialog first
        AlertDialog dialog = new AlertDialog.Builder(mContext)
                .setTitle(R.string.desktop_shortcuts)
                .setView(view)
                .setNegativeButton(R.string.close, null)
                .setPositiveButton(R.string.refresh, (d, w) -> refreshShortcuts(grid))
                .create();
        
        this.currentDialog = dialog;
        dialog.show();
grid.post(() -> displayDesktopShortcuts(grid));
        // Then process LNK files and populate the grid
        new ProcessLnkFilesTask(grid, dialog).execute();
    }

    /** Get wine type from shared preferences **/
    private String getWineType() {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString("WINE_TYPE", "bionic"); // default to bionic if not set
        } catch (Exception e) {
            e.printStackTrace();
            return "bionic"; // fallback to bionic
        }
    }

    /** Get app-specific wine type from shared preferences **/
    private String getAppWineType(String appName) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString("WINE_TYPE_" + appName, getWineType()); // default to global wine type
        } catch (Exception e) {
            e.printStackTrace();
            return getWineType();
        }
    }

    /** Save app-specific wine type from shared preferences **/
    private void saveAppWineType(String appName, String wineType) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("WINE_TYPE_" + appName, wineType);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Get app-specific DXVK setting from shared preferences **/
    private String getAppDxvk(String appName) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString("DXVK_" + appName, "<none>"); // default to none
        } catch (Exception e) {
            e.printStackTrace();
            return "<none>";
        }
    }

    /** Save app-specific DXVK setting to shared preferences **/
    private void saveAppDxvk(String appName, String dxvk) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("DXVK_" + appName, dxvk);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Get app-specific driver setting from shared preferences **/
    private String getAppDriver(String appName) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString("DRIVER_" + appName, "<none>"); // default to none
        } catch (Exception e) {
            e.printStackTrace();
            return "<none>";
        }
    }

    /** Save app-specific driver setting to shared preferences **/
    private void saveAppDriver(String appName, String driver) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("DRIVER_" + appName, driver);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Get app-specific cores setting from shared preferences **/
    private String getAppCores(String appName) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            return prefs.getString("CORES_" + appName, "0"); // default to no core restriction
        } catch (Exception e) {
            e.printStackTrace();
            return "0";
        }
    }

    /** Save app-specific cores setting to shared preferences **/
    private void saveAppCores(String appName, String cores) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putString("CORES_" + appName, cores);
            editor.apply();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /** Delete all app-specific settings from shared preferences **/
    private void deleteAppSettings(String appName) {
        try {
            SharedPreferences prefs = mContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.remove("WINE_TYPE_" + appName);
            editor.remove("DXVK_" + appName);
            editor.remove("DRIVER_" + appName);
            editor.remove("CORES_" + appName);
            editor.apply();
            Toast.makeText(mContext, "Settings reset for " + appName, Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(mContext, "Failed to reset settings", Toast.LENGTH_SHORT).show();
        }
    }

    /** Show app-specific settings dialog **/
    private void showAppSettings(String appName, String execPath) {
        View view = LayoutInflater.from(mContext).inflate(R.layout.app_settings_dialog, null);
        
        Spinner dxvkSpinner = view.findViewById(R.id.dxvkSpinner);
        Spinner driverSpinner = view.findViewById(R.id.driverSpinner);
        Spinner coresSpinner = view.findViewById(R.id.coresSpinner);

        // Load current settings for this app
        String currentWine = getAppWineType(appName);
        String currentDxvk = getAppDxvk(appName);
        String currentDriver = getAppDriver(appName);
        String currentCores = getAppCores(appName);

        // Setup cores spinner
        String[] coreOptions = {
            "No core restriction", 
            "cores 1 only (6-7)", 
            "cores 2 only (5-7)", 
            "cores 3 only (4-7)", 
            "cores 4 only (3-7)", 
            "cores 6 only (2-7)", 
            "cores 6 only (1-7)", 
            "cores 7 only (0-7)"
        };
        ArrayAdapter<String> coresAdapter = new ArrayAdapter<>(mContext, 
            android.R.layout.simple_spinner_dropdown_item, coreOptions);
        coresSpinner.setAdapter(coresAdapter);
        int coreSelection = getCoreSelectionIndex(currentCores);
        coresSpinner.setSelection(coreSelection);

        // Load DXVK and driver lists
        loadDxvkListForWine(currentWine, dxvkSpinner, currentDxvk);
        loadDriverListForWine(currentWine, driverSpinner, currentDriver);

        new AlertDialog.Builder(mContext)
            .setTitle(mContext.getString(R.string.app_settings_title, appName))
            .setView(view)
            .setPositiveButton(R.string.save, (dialog, which) -> {
                // Save all settings
                String selectedDxvk = dxvkSpinner.getSelectedItem().toString();
                String selectedDriver = driverSpinner.getSelectedItem().toString();
                int selectedCoresIndex = coresSpinner.getSelectedItemPosition();
                String selectedCores = getCoresValueFromIndex(selectedCoresIndex);

                saveAppDxvk(appName, selectedDxvk);
                saveAppDriver(appName, selectedDriver);
                saveAppCores(appName, selectedCores);
                saveCoreSettingsToConfig(appName, selectedCores);
                Toast.makeText(mContext, R.string.settings_saved, Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton(R.string.cancel, null)
            .setNeutralButton(R.string.reset_settings, (dialog, which) -> {
                // Reset all settings for this app
                deleteAppSettings(appName);
            })
            .show();
    }

    /** Apply app-specific DXVK and driver settings before launch - USING BACKGROUND THREAD **/
    private void applyAppSpecificSettings(String appName, String wineType, ExtractionCallback callback) {
        new Thread(() -> {
            boolean success = true;
            String dxvk = getAppDxvk(appName);
            String driver = getAppDriver(appName);
            
            Log.d("ExtractionDebug", "Starting background extraction for app: " + appName);
            Log.d("ExtractionDebug", "DXVK: " + dxvk + ", Driver: " + driver);
            
            // Update progress on UI thread
            ((android.app.Activity) mContext).runOnUiThread(() -> {
                if (extractionProgressDialog == null) {
                    extractionProgressDialog = new ProgressDialog(mContext);
                    extractionProgressDialog.setMessage("Applying settings...");
                    extractionProgressDialog.setCancelable(false);
                }
                extractionProgressDialog.show();
            });

            try {
                // Extract DXVK if needed
                if (!"<none>".equals(dxvk)) {
                    ((android.app.Activity) mContext).runOnUiThread(() -> 
                        extractionProgressDialog.setMessage("Extracting DXVK..."));
                    
                    String dxvkSrc = USR_PREFIX + "/glibc/opt/libs/d3d/" + dxvk;
                    String winePrefix = "glibc".equals(wineType) ? 
                        USR_PREFIX + "/glibc/xod9.9/.wine" : 
                        termuxHome + "/.wine";
                    String dxvkExtractPath = winePrefix + "/drive_c/windows";
                    
                    String dxvkCommand = USR_PREFIX + "/bin/7z x '" + dxvkSrc + "' -o'" + dxvkExtractPath + "' -y";
                    boolean dxvkSuccess = execShellXoDosStyle(dxvkCommand);
                    success = success && dxvkSuccess;
                    
                    Log.d("ExtractionDebug", "DXVK extraction " + (dxvkSuccess ? "succeeded" : "failed"));
                }

                // Extract driver if needed
                if (!"<none>".equals(driver)) {
                    ((android.app.Activity) mContext).runOnUiThread(() -> 
                        extractionProgressDialog.setMessage("Extracting driver..."));
                    
                    String driverSrc;
                    String driverExtractPath;
                    if ("glibc".equals(wineType)) {
                        driverSrc = USR_PREFIX + "/glibc/opt/libs/mesa/" + driver;
                        driverExtractPath = USR_PREFIX + "/glibc";
                    } else {
                        driverSrc = USR_PREFIX + "/drivers/25/" + driver;
                        driverExtractPath = USR_PREFIX + "/drivers/mesa-242";
                    }
                    
                    String driverCommand = USR_PREFIX + "/bin/7z x '" + driverSrc + "' -o'" + driverExtractPath + "' -y";
                    boolean driverSuccess = execShellXoDosStyle(driverCommand);
                    success = success && driverSuccess;
                    
                    Log.d("ExtractionDebug", "Driver extraction " + (driverSuccess ? "succeeded" : "failed"));
                }

                // Small delay to ensure extraction completes
                Thread.sleep(2000);

            } catch (Exception e) {
                Log.e("ExtractionDebug", "Extraction error: " + e.getMessage());
                success = false;
            } finally {
                // Hide progress dialog
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    if (extractionProgressDialog != null && extractionProgressDialog.isShowing()) {
                        extractionProgressDialog.dismiss();
                    }
                });
                
                // Callback with result
                callback.onExtractionComplete(success);
            }
        }).start();
    }

    /** Callback interface for extraction completion **/
    interface ExtractionCallback {
        void onExtractionComplete(boolean success);
    }

    /** Execute shell command using EXACT XoDosWizard method **/
    private boolean execShellXoDosStyle(String cmd) {
        try {
            Log.d("ShellCommand", "Executing: " + cmd);
            
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", cmd);
            
            // Set Termux environment exactly like XoDosWizard
            Map<String, String> env = processBuilder.environment();
            env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin");
            env.put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");
            env.put("PREFIX", "/data/data/com.termux/files/usr");
            env.put("HOME", "/data/data/com.termux/files/home");
            env.put("TMPDIR", "/data/data/com.termux/files/usr/tmp");
            
            Process process = processBuilder.start();
            
            // Capture output and error streams
            BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            String s;
            StringBuilder output = new StringBuilder();
            StringBuilder error = new StringBuilder();
            
            while ((s = stdInput.readLine()) != null) {
                output.append(s).append("\n");
            }
            
            while ((s = stdError.readLine()) != null) {
                error.append(s).append("\n");
            }
            
            int exitCode = process.waitFor();
            
            // Log the results with more detail
            if (output.length() > 0) {
                Log.d("ShellOutput", "Output: " + output.toString());
            }
            if (error.length() > 0) {
                Log.e("ShellError", "Error: " + error.toString());
            }
            Log.d("ShellCommand", "Exit code: " + exitCode);
            
            return exitCode == 0;
            
        } catch (Exception e) {
            Log.e("ShellCommand", "Exception executing: " + cmd, e);
            return false;
        }
    }
    
    /** Helper method to set spinner selection **/
    private void setSpinnerSelection(Spinner spinner, String value) {
        ArrayAdapter<String> adapter = (ArrayAdapter<String>) spinner.getAdapter();
        if (adapter != null) {
            for (int i = 0; i < adapter.getCount(); i++) {
                if (adapter.getItem(i).equals(value)) {
                    spinner.setSelection(i);
                    return;
                }
            }
        }
    }

    /** Convert core selection index to value **/
    private int getCoreSelectionIndex(String cores) {
        if (cores == null || "0".equals(cores)) return 0;
        switch (cores) {
            case "1": return 1;
            case "2": return 2;
            case "3": return 3;
            case "4": return 4;
            case "6": return 5;
            case "7": return 6;
            case "8": return 7;
            default: return 0;
        }
    }

    /** Get cores value from selection index **/
    private String getCoresValueFromIndex(int index) {
        switch (index) {
            case 1: return "1";
            case 2: return "2";
            case 3: return "3";
            case 4: return "4";
            case 5: return "6";
            case 6: return "7";
            case 7: return "8";
            default: return "0";
        }
    }

    /** Load DXVK list for wine type **/
    private void loadDxvkListForWine(String wineType, Spinner spinner, String selectIfFound) {
        File dxvkDir = new File(USR_PREFIX + "/glibc/opt/libs/d3d");
        List<String> list = listArchiveFiles(dxvkDir);
        updateSpinner(spinner, list, selectIfFound);
    }

    /** Load driver list for wine type **/
    private void loadDriverListForWine(String wineType, Spinner spinner, String selectIfFound) {
        File driverDir;
        if ("glibc".equals(wineType)) {
            driverDir = new File(USR_PREFIX + "/glibc/opt/libs/mesa");
        } else {
            driverDir = new File(USR_PREFIX + "/drivers/25");
        }
        List<String> list = listArchiveFiles(driverDir);
        updateSpinner(spinner, list, selectIfFound);
    }

    /** List archive files in directory **/
    private List<String> listArchiveFiles(File dir) {
        List<String> files = new ArrayList<>();
        files.add("<none>");
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".7z") || f.getName().endsWith(".tar.gz")) {
                    files.add(f.getName());
                }
            }
        }
        return files;
    }

    /** Update spinner with items **/
    private void updateSpinner(Spinner spinner, List<String> items, String selectIfFound) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mContext, 
            android.R.layout.simple_spinner_dropdown_item, items);
        spinner.setAdapter(adapter);
        setSpinnerSelection(spinner, selectIfFound);
    }

    /** Show enhanced launch confirmation dialog **/
    private void showLaunchConfirmation(String name, String execPath) {
        // Get the app-specific settings
        String wineType = getAppWineType(name);
        String wineTypeDisplay = "glibc".equals(wineType) ? 
            mContext.getString(R.string.wine_type_glibc) : 
            mContext.getString(R.string.wine_type_bionic);
        
        String dxvk = getAppDxvk(name);
        String driver = getAppDriver(name);
        String cores = getAppCores(name);
        
        StringBuilder message = new StringBuilder();
        message.append(mContext.getString(R.string.launch_confirmation_message, wineTypeDisplay));
        
        if (!"<none>".equals(dxvk)) {
            message.append("\n• DXVK: ").append(dxvk);
        }
        if (!"<none>".equals(driver)) {
            message.append("\n• Driver: ").append(driver);
        }
        if (!"0".equals(cores)) {
            message.append("\n• Cores: ").append(cores);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.launch_app_title, name))
                .setMessage(message.toString())
                .setPositiveButton(R.string.launch, (d, which) -> {
                    // Apply DXVK and driver settings using background thread
                    applyAppSpecificSettings(name, wineType, new ExtractionCallback() {
                        @Override
                        public void onExtractionComplete(boolean success) {
                            ((android.app.Activity) mContext).runOnUiThread(() -> {
                                if (success) {
                                    Toast.makeText(mContext, "Settings applied, launching...", Toast.LENGTH_SHORT).show();
                                    launchShortcut(name, execPath, wineType);
                                } else {
                                    Toast.makeText(mContext, "Failed to apply some settings, launching anyway...", Toast.LENGTH_LONG).show();
                                    launchShortcut(name, execPath, wineType);
                                }
                            });
                        }
                    });
                })
                .setNegativeButton(R.string.cancel, null)
                .setNeutralButton(R.string.settings, (d, which) -> {
                    // Show settings for this app
                    showAppSettings(name, execPath);
                });

        builder.show();
    }

    // Refresh shortcuts and icons
    private void refreshShortcuts(GridLayout grid) {
        new ProcessLnkFilesTask(grid, currentDialog).execute();
    }

    // AsyncTask to process LNK files and generate .desktop files
    private class ProcessLnkFilesTask extends AsyncTask<Void, Void, Void> {
        private final GridLayout grid;
        private final AlertDialog dialog;
        
        public ProcessLnkFilesTask(GridLayout grid, AlertDialog dialog) {
            this.grid = grid;
            this.dialog = dialog;
        }

        @Override
        protected Void doInBackground(Void... params) {
            File[] lnkFiles = desktopDir2.listFiles((dir, name) -> name.endsWith(".lnk"));
            if (lnkFiles == null) return null;

            for (File lnk : lnkFiles) {
                try {
                    String exePath = extractExePath(lnk);
                    if (exePath == null) {
                        System.err.println("Failed to extract EXE path from: " + lnk.getName());
                        continue;
                    }

                    System.out.println("Extracted path from LNK: " + exePath);
                    // Generate .desktop file from LNK
                    generateDesktopFromLnk(lnk, exePath);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            // Now display all .desktop files (including newly generated ones)
            displayDesktopShortcuts(grid);
        }
    }

    // Load icon with refresh capability
    private void loadIconWithRefresh(ImageView icon, String iconPath, String appName) {
        File iconFile = new File(iconPath);
        if (!iconPath.isEmpty() && iconFile.exists()) {
            // Try to load the icon
            Bitmap bitmap = BitmapFactory.decodeFile(iconPath);
            if (bitmap != null) {
                icon.setImageBitmap(bitmap);
            } else {
                // If bitmap is null, the file might be corrupted or empty
                icon.setImageResource(android.R.drawable.ic_menu_slideshow);
                // Try to regenerate the icon
                regenerateIconForApp(appName);
            }
        } else {
            icon.setImageResource(android.R.drawable.ic_menu_slideshow);
            // Try to regenerate the icon if it doesn't exist
            regenerateIconForApp(appName);
        }
    }

    // Regenerate icon for a specific app
    private void regenerateIconForApp(String appName) {
        new Thread(() -> {
            try {
                // Find the desktop file for this app
                File desktopFile = new File(desktopDir, appName + ".desktop");
                if (desktopFile.exists()) {
                    Map<String, String> entry = parseDesktopFile(desktopFile);
                    String execPath = entry.get("Exec");
                    if (execPath != null) {
                        // Extract the actual EXE path from the Exec line
                        String exePath = extractExeFromExecLine(execPath);
                        if (exePath != null) {
                            File iconFile = new File(iconDir, appName + ".png");
                            generateIcon(exePath, iconFile, true);
                        }
                    }
                }
            } catch (Exception e) {
                Log.e("IconRefresh", "Failed to regenerate icon for " + appName + ": " + e.getMessage());
            }
        }).start();
    }

    // Display all .desktop shortcuts in the grid - FIXED VERSION
/** Display all .desktop shortcuts in the grid - SAFE & /** Display all .desktop shortcuts in the grid - rotation-safe version **/
private void displayDesktopShortcuts(GridLayout grid) {
    File[] files = desktopDir.listFiles((dir, name) -> name.endsWith(".desktop"));
    if (files == null || files.length == 0) {
        Toast.makeText(mContext, R.string.no_shortcuts_found, Toast.LENGTH_SHORT).show();
        return;
    }

    // Reset layout first to avoid stale row/column specs
    grid.removeAllViews();
    grid.setRowCount(GridLayout.UNDEFINED);
    grid.setColumnCount(GridLayout.UNDEFINED);

    // Dynamic column count based on screen width
    DisplayMetrics dm = mContext.getResources().getDisplayMetrics();
    int minColumnWidth = (int)(150 * dm.density);
    int columnCount = Math.max(2, dm.widthPixels / minColumnWidth);
    grid.setColumnCount(columnCount);

    float density = dm.density;
    int iconSize = (int)(80 * density);
    int buttonSize = (int)(40 * density);

    for (File f : files) {
        Map<String, String> entry = parseDesktopFile(f);
        String name = entry.getOrDefault("Name", f.getName().replace(".desktop", ""));
        String iconPath = entry.getOrDefault("Icon", "");
        String execPath = entry.getOrDefault("Exec", "");

        // Item container
        LinearLayout item = new LinearLayout(mContext);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setGravity(Gravity.CENTER_HORIZONTAL);
        item.setPadding((int)(8*density),(int)(8*density),(int)(8*density),(int)(8*density));

        // Flexible layout params
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
        lp.width = 0;
        lp.height = GridLayout.LayoutParams.WRAP_CONTENT;
        lp.rowSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f);
        lp.setMargins((int)(4*density),(int)(4*density),(int)(4*density),(int)(4*density));
        item.setLayoutParams(lp);

        // Icon
        ImageView icon = new ImageView(mContext);
        loadIconWithRefresh(icon, iconPath, name);
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconLp.gravity = Gravity.CENTER_HORIZONTAL;
        icon.setLayoutParams(iconLp);

        // Label
        TextView label = new TextView(mContext);
        label.setText(name);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        label.setTextSize(12f);
        label.setPadding(0, (int)(4*density), 0, (int)(8*density));

        // Horizontal button container
        LinearLayout buttonLayout = new LinearLayout(mContext);
        buttonLayout.setOrientation(LinearLayout.HORIZONTAL);
        buttonLayout.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams buttonLayoutParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT, 
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        buttonLayout.setLayoutParams(buttonLayoutParams);

        // Open button with icon
        ImageButton openBtn = new ImageButton(mContext);
        try {
            openBtn.setImageResource(R.drawable.iopn);
        } catch (Exception e) {
            openBtn.setImageResource(android.R.drawable.ic_media_play);
        }
        openBtn.setBackgroundColor(Color.BLACK); 
     //   openBtn.setBackgroundResource(android.R.drawable.btn_default_small);
        LinearLayout.LayoutParams openBtnParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        openBtnParams.setMargins(0, 0, (int)(4*density), 0);
        openBtn.setLayoutParams(openBtnParams);
        openBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        openBtn.setPadding((int)(4*density), (int)(4*density), (int)(4*density), (int)(4*density));
        openBtn.setOnClickListener(v -> showLaunchConfirmation(name, execPath));
        openBtn.setContentDescription("Open " + name);

        // Delete button with icon
        ImageButton deleteBtn = new ImageButton(mContext);
        try {
            deleteBtn.setImageResource(R.drawable.idel_l);
        } catch (Exception e) {
            deleteBtn.setImageResource(android.R.drawable.ic_delete);
        }
        deleteBtn.setBackgroundColor(Color.BLACK); 
     //   deleteBtn.setBackgroundResource(android.R.drawable.btn_default_small);
        LinearLayout.LayoutParams deleteBtnParams = new LinearLayout.LayoutParams(buttonSize, buttonSize);
        deleteBtnParams.setMargins((int)(4*density), 0, 0, 0);
        deleteBtn.setLayoutParams(deleteBtnParams);
        deleteBtn.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        deleteBtn.setPadding((int)(4*density), (int)(4*density), (int)(4*density), (int)(4*density));
        deleteBtn.setOnClickListener(v -> confirmDelete(f, grid, name));
        deleteBtn.setContentDescription("Delete " + name);

        // Add buttons to horizontal layout
        buttonLayout.addView(openBtn);
        buttonLayout.addView(deleteBtn);

        // Add everything
        item.addView(icon);
        item.addView(label);
        item.addView(buttonLayout);

        grid.addView(item);
    }

    grid.invalidate();
    grid.requestLayout();
    
    View parent = (View) grid.getParent();
if (parent instanceof ScrollView) {
    parent.post(() -> ((ScrollView) parent).fullScroll(View.FOCUS_UP));
}
}
    // Extract EXE path from Exec line
    private String extractExeFromExecLine(String execLine) {
        // Exec line format: /path/to/wine "/path/to/exe"
        // We need to extract the part inside quotes
        if (execLine.contains("\"")) {
            int start = execLine.indexOf("\"") + 1;
            int end = execLine.lastIndexOf("\"");
            if (end > start) {
                return execLine.substring(start, end);
            }
        }
        return null;
    }

    /** Save core settings to xodwine.cfg file **/
    private void saveCoreSettingsToConfig(String appName, String cores) {
        try {
            File configFile = new File(termuxHome + "/xodwine.cfg");
            Map<String, String> config = new HashMap<>();
            
            // Read existing config if it exists
            if (configFile.exists()) {
                try (BufferedReader br = new BufferedReader(new FileReader(configFile))) {
                    String line;
                    while ((line = br.readLine()) != null) {
                        if (line.contains("=")) {
                            String[] parts = line.split("=", 2);
                            if (parts.length == 2) {
                                config.put(parts[0].trim(), parts[1].trim());
                            }
                        }
                    }
                }
            }
            
            // Convert core setting to primary/secondary cores format (same as wizard)
            String primaryCores = null;
            String secondaryCores = null;
            
            if (!"0".equals(cores)) {
                switch (cores) {
                    case "1":
                        primaryCores = "6-7";
                        secondaryCores = "0-5";
                        break;
                    case "2":
                        primaryCores = "5-7";
                        secondaryCores = "0-4";
                        break;
                    case "3":
                        primaryCores = "4-7";
                        secondaryCores = "0-3";
                        break;
                    case "4":
                        primaryCores = "3-7";
                        secondaryCores = "0-2";
                        break;
                    case "6":
                        primaryCores = "2-7";
                        secondaryCores = "0-1";
                        break;
                    case "7":
                        primaryCores = "1-7";
                        secondaryCores = "0-1";
                        break;
                    case "8":
                        primaryCores = "0-7";
                        secondaryCores = "0-1";
                        break;
                }
            }
            
            // Update core settings in config
            if (primaryCores != null && secondaryCores != null) {
                config.put("PRIMARY_CORES", primaryCores);
                config.put("SECONDARY_CORES", secondaryCores);
            } else {
                config.remove("PRIMARY_CORES");
                config.remove("SECONDARY_CORES");
            }
            
            // Add app identifier comment
            config.put("# APP_CORES_FOR", appName);
            
            // Write updated config back to file
            try (FileWriter writer = new FileWriter(configFile)) {
                for (Map.Entry<String, String> entry : config.entrySet()) {
                    if (entry.getKey().startsWith("#")) {
                        writer.write(entry.getKey() + " " + entry.getValue() + "\n");
                    } else {
                        writer.write(entry.getKey() + "=" + entry.getValue() + "\n");
                    }
                }
            }
            
            Log.d("ConfigSave", "Core settings saved to xodwine.cfg for app: " + appName);
            
        } catch (Exception e) {
            Log.e("ConfigSave", "Failed to save core settings to xodwine.cfg: " + e.getMessage());
        }
    }

    // Launch shortcut using terminal session
    private void launchShortcut(String name, String execPath, String wineType) {
        if (!(mContext instanceof TermuxActivity)) {
            launchWithProcessBuilder(name, execPath);
            return;
        }

        TermuxActivity termuxActivity = (TermuxActivity) mContext;

        try {
            // Use the terminal session client to execute the command
            String command = "termux-x11 :0 >/dev/null 2>&1 & pkill -f 'wine'; sleep 3 && " + execPath + " > /sdcard/xodos_shortcuts-logs.txt 2>&1" + "\n";

            Log.d("LaunchCommand", "Executing: " + command);

            // Write directly to the current session
            if (termuxActivity.getTermuxTerminalSessionClient() != null) {
                TerminalSession session = termuxActivity.getTermuxTerminalSessionClient().getCurrentStoredSessionOrLast();
                if (session != null && session.isRunning()) {
                    // write the command to the session
                    session.write(command);
                    showTerminalView(termuxActivity);
                    Toast.makeText(mContext, mContext.getString(R.string.launching_app, name), Toast.LENGTH_SHORT).show();

                    // START: monitor thread that checks the X11 display periodically
                    final String appNameForToast = name;
                    new Thread(() -> {
                        try {
                            final int CHECK_INTERVAL_MS = 5000; // 5 seconds
                            final int MAX_CHECKS = 6;           // 30 seconds total
                            boolean appVisible = false;

                            TermuxActivity termuxActivityLocal = (TermuxActivity) mContext;
                            TerminalSession sessionLocal = null;
                            if (termuxActivityLocal.getTermuxTerminalSessionClient() != null) {
                                sessionLocal = termuxActivityLocal.getTermuxTerminalSessionClient().getCurrentStoredSessionOrLast();
                            }

                            if (sessionLocal == null || !sessionLocal.isRunning()) {
                                Log.e("X11Check", "No active Termux session to run visibility checks.");
                                return;
                            }

                            for (int i = 0; i < MAX_CHECKS; i++) {
                                Thread.sleep(CHECK_INTERVAL_MS);

                                // Send a command into Termux to check visible windows and store result in a temp file
                                sessionLocal.write("DISPLAY=:0 xdotool search --onlyvisible --name . > /sdcard/exe_check.txt 2>/dev/null\n");
                                Thread.sleep(2000); // wait 2s for command to execute

                                // Now read result from that file using Java (Termux's FS)
                                File checkFile = new File("/sdcard/exe_check.txt");
                                if (checkFile.exists()) {
                                    try (BufferedReader br = new BufferedReader(new FileReader(checkFile))) {
                                        String line = br.readLine();
                                        Log.d("X11Check", "Check #" + (i + 1) + " visible window id: " + line);
                                        if (line != null && !line.trim().isEmpty()) {
                                            appVisible = true;
                                            Log.d("X11Check", "✅ Window detected inside Termux, stopping monitor thread.");
                                            break;
                                        }
                                    } catch (Exception e) {
                                        Log.e("X11Check", "Error reading /tmp/x11_check.txt: " + e.getMessage());
                                    }
                                }
                            }

                            if (!appVisible) {
                                Log.w("LaunchMonitor", "No visible window detected within timeout; closing termux-x11");
                                try {
                                    sessionLocal.write("pkill -f termux.x11 || pkill -f termux-x11\n");
                                } catch (Exception e) {
                                    Log.e("LaunchMonitor", "Failed to kill termux.x11 from Termux session: " + e.getMessage());
                                }

                                if (mContext instanceof android.app.Activity) {
                                    ((android.app.Activity) mContext).runOnUiThread(() ->
                                            Toast.makeText(mContext,
                                                    mContext.getString(R.string.failed_to_open_exe, appNameForToast),
                                                    Toast.LENGTH_LONG).show()
                                    );
                                }
                            }

                        } catch (InterruptedException ie) {
                            Log.e("LaunchMonitor", "Monitor interrupted: " + ie.getMessage());
                        }
                    }).start();
                    // END: monitor thread

                    // Close the dialog / show terminal as before
                    closeAndShowTerminal();
                    return;
                }
            }

            // Fallback if we couldn't use the session
            launchWithProcessBuilder(name, execPath);

        } catch (Exception e) {
            Log.e("LaunchError", "Failed to launch: " + e.getMessage());
            launchWithProcessBuilder(name, execPath);
        }
    }

    // Fallback method using ProcessBuilder
    private void launchWithProcessBuilder(String name, String execPath) {
        new Thread(() -> {
            try {
                String fullCommand = "termux-x11 :0 >/dev/null 2>&1 & pkill -f 'wine'; sleep 3 && " + execPath;
                
                Log.d("LaunchCommand", "Fallback executing: " + fullCommand);
                
                ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", fullCommand);
                
                // Set Termux environment
                Map<String, String> env = processBuilder.environment();
                env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin");
                env.put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");
                env.put("PREFIX", "/data/data/com.termux/files/usr");
                env.put("HOME", "/data/data/com.termux/files/home");
                env.put("TMPDIR", "/data/data/com.termux/files/usr/tmp");
                
                processBuilder.redirectErrorStream(true);
                Process process = processBuilder.start();
                
                // Read output
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    Log.d("WineOutput", line);
                }
                
                int exitCode = process.waitFor();
                Log.d("Launch", "Exit code: " + exitCode);
                
                // Handle result
                if (mContext instanceof android.app.Activity) {
                    ((android.app.Activity) mContext).runOnUiThread(() -> {
                        if (exitCode == 0) {
                            Toast.makeText(mContext, mContext.getString(R.string.launch_success, name), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(mContext, mContext.getString(R.string.launch_failed, name), Toast.LENGTH_LONG).show();
                        }
                    });
                }
                
            } catch (Exception e) {
                Log.e("LaunchError", "Fallback also failed: " + e.getMessage());
                if (mContext instanceof android.app.Activity) {
                    ((android.app.Activity) mContext).runOnUiThread(() -> 
                        showErrorDialog(mContext.getString(R.string.launch_error), 
                            mContext.getString(R.string.launch_error_detailed, name, e.getClass().getSimpleName(), e.getMessage()))
                    );
                }
            }
        }).start();
    }

    // Show terminal view
    private void showTerminalView(TermuxActivity termuxActivity) {
        if (termuxActivity.getMainContentView() != null) {
            termuxActivity.getMainContentView().setTerminalViewSwitchSlider(true);
        }
    }

    // Close the shortcuts dialog and show terminal
    private void closeAndShowTerminal() {
        if (currentDialog != null && currentDialog.isShowing()) {
            currentDialog.dismiss();
        }
        if (mContext instanceof TermuxActivity) {
            showTerminalView((TermuxActivity) mContext);
        }
    }

    // Generate .desktop file from LNK file
    private void generateDesktopFromLnk(File lnkFile, String exePath) {
        try {
            String baseName = lnkFile.getName().replace(".lnk", "");
            
            // Get current wine type to convert the path
            String wineType = getWineType();
            String convertedPath = convertToTermuxPath(exePath, wineType);
            String wineCommand = getWineCommand(wineType);
            
            System.out.println("Converting path: " + exePath + " -> " + convertedPath);

            // Generate icon - try even if path might be wrong
            File iconFile = new File(iconDir, baseName + ".png");
            if (!iconFile.exists()) {
                generateIcon(convertedPath, iconFile, false);
            }

            // Create .desktop file - store with Wine command for XFCE desktop
            File desktopFile = new File(desktopDir, baseName + ".desktop");
            if (!desktopFile.exists()) {
                try (PrintWriter out = new PrintWriter(desktopFile)) {
                    out.println("[Desktop Entry]");
                    out.println("Type=Application");
                    out.println("Name=" + baseName);
                    // Store with Wine command for XFCE compatibility
                    out.println("Exec=" + wineCommand + " \"" + convertedPath + "\"");
                    out.println("Icon=" + iconFile.getAbsolutePath());
                    out.println("Terminal=false");
                    out.println("Categories=Game;");
                    System.out.println("Created desktop file: " + desktopFile.getAbsolutePath());
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Get the appropriate Wine command based on type
    private String getWineCommand(String wineType) {
        if ("glibc".equals(wineType)) {
            return USR_PREFIX + "/glibc/opt/scripts/xodos_wine";
        } else {
            return USR_PREFIX + "/bin/xbio";
        }
    }

    // Convert Windows path to Termux path
    private String convertToTermuxPath(String windowsPath, String wineType) {
        if (windowsPath != null && windowsPath.matches("^[A-Z]:\\\\.*")) {
            String basePath;
            if ("glibc".equals(wineType)) {
                basePath = "/data/data/com.termux/files/usr/glibc/xod9.9/.wine/dosdevices/";
            } else {
                basePath = "/data/data/com.termux/files/home/.wine/dosdevices/";
            }
            
            // Extract drive letter and path
            String driveLetter = windowsPath.substring(0, 1).toLowerCase();
            String remainingPath = windowsPath.substring(3).replace("\\", "/");
            
            String fullPath = basePath + driveLetter + ":/" + remainingPath;
            System.out.println("Converted Windows path to Termux: " + windowsPath + " -> " + fullPath);
            return fullPath;
        }
        return windowsPath;
    }

    // Generate icon from EXE file
    private void generateIcon(String exePath, File iconFile, boolean refreshAfter) {
        // Create final copies for use in the thread
        final String finalExePath = exePath;
        final File finalIconFile = iconFile;
        final boolean shouldRefresh = refreshAfter;
        
        new Thread(() -> {
            String currentExePath = finalExePath;
            File currentExeFile = new File(currentExePath);
            
            try {
                if (!currentExeFile.exists()) {
                    System.err.println("EXE file does not exist: " + currentExePath);
                    // Try alternative locations
                    String[] alternativePaths = tryAlternativePaths(currentExePath);
                    for (String altPath : alternativePaths) {
                        File altFile = new File(altPath);
                        if (altFile.exists()) {
                            System.out.println("Found alternative path: " + altPath);
                            currentExePath = altPath;
                            currentExeFile = altFile;
                            break;
                        }
                    }
                }
                
                if (currentExeFile.exists()) {
                    String cmd = "wrestool -x --type=14 \"" + currentExePath + "\" 2>/dev/null | magick ico:- \"" + finalIconFile.getAbsolutePath() + "\" 2>/dev/null";
                    System.out.println("Running icon command: " + cmd);
                    
                    // Use ProcessBuilder with proper Termux environment
                    ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", cmd);
                    
                    // Set environment variables for Termux
                    Map<String, String> env = processBuilder.environment();
                    env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin");
                    env.put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");
                    env.put("PREFIX", "/data/data/com.termux/files/usr");
                    env.put("HOME", "/data/data/com.termux/files/home");
                    env.put("TMPDIR", "/data/data/com.termux/files/usr/tmp");
                    
                    Process process = processBuilder.start();
                    
                    // Read output streams
                    BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
                    BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()));
                    
                    String line;
                    StringBuilder output = new StringBuilder();
                    StringBuilder error = new StringBuilder();
                    
                    while ((line = stdInput.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                    while ((line = stdError.readLine()) != null) {
                        error.append(line).append("\n");
                    }
                    
                    int exitCode = process.waitFor();
                    Log.d("IconGen", "Process finished with exit code: " + exitCode);
                    
                    if (exitCode == 0) {
                        Log.i("IconGen", "Icon generated successfully: " + finalIconFile.getAbsolutePath());
                        // Refresh the grid if requested
                        if (shouldRefresh && mContext instanceof android.app.Activity) {
                            ((android.app.Activity) mContext).runOnUiThread(() -> {
                                if (currentGrid != null) {
                                    displayDesktopShortcuts(currentGrid);
                                }
                            });
                        }
                    } else {
                        Log.e("IconGen", "Icon generation failed with exit code: " + exitCode);
                        if (output.length() > 0) Log.d("IconGenOutput", output.toString());
                        if (error.length() > 0) Log.e("IconGenError", error.toString());
                        createDefaultIcon(finalIconFile);
                    }
                } else {
                    Log.e("IconGen", "Cannot generate icon, EXE not found: " + currentExePath);
                    createDefaultIcon(finalIconFile);
                }
                
            } catch (Exception e) {
                Log.e("IconGen", "Error generating icon: " + e.getMessage());
                e.printStackTrace();
                try {
                    createDefaultIcon(finalIconFile);
                } catch (Exception ex) {
                    ex.printStackTrace();
                }
            }
        }).start();
    }

    // Try alternative paths if the main path doesn't exist
    private String[] tryAlternativePaths(String originalPath) {
        List<String> alternatives = new ArrayList<>();
        
        // Try different Wine prefixes
        alternatives.add(originalPath.replace("/usr/glibc/xod9.9/.wine/dosdevices/", "/home/.wine/dosdevices/"));
        alternatives.add(originalPath.replace("/home/.wine/dosdevices/", "/usr/glibc/xod9.9/.wine/dosdevices/"));
        
        // Try without the full path (just filename)
        String fileName = new File(originalPath).getName();
        alternatives.add("/data/data/com.termux/files/usr/glibc/xod9.9/.wine/dosdevices/c:/windows/system32/" + fileName);
        alternatives.add("/data/data/com.termux/files/home/.wine/dosdevices/c:/windows/system32/" + fileName);
        
        return alternatives.toArray(new String[0]);
    }

    // Create a default icon if extraction fails
    private void createDefaultIcon(File iconFile) {
        try {
            if (!iconFile.exists()) {
                iconFile.createNewFile();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Improved method to extract EXE path from LNK file
    private String extractExePath(File lnkFile) {
        try (FileInputStream fis = new FileInputStream(lnkFile)) {
            byte[] buffer = new byte[(int) lnkFile.length()];
            fis.read(buffer);
            
            // Try multiple encodings
            String[] encodings = {"UTF-16LE", "ISO-8859-1", "UTF-8"};
            List<String> allCandidates = new ArrayList<>();
            
            for (String encoding : encodings) {
                try {
                    String content = new String(buffer, encoding);
                    List<String> candidates = findPathsInContent(content);
                    allCandidates.addAll(candidates);
                } catch (Exception e) {
                    // Ignore encoding errors
                }
            }
            
            // Also try raw byte analysis for LNK structure
            List<String> rawCandidates = analyzeLnkStructure(buffer);
            allCandidates.addAll(rawCandidates);
            
            // Filter and select the best candidate
            return selectBestPath(allCandidates, lnkFile.getName());
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        System.err.println("Could not extract path from LNK: " + lnkFile.getName());
        return null;
    }

    // Find paths in string content
    private List<String> findPathsInContent(String content) {
        List<String> candidates = new ArrayList<>();
        
        // Pattern for Windows paths with .exe
        Pattern exePattern = Pattern.compile("[A-Z]:\\\\[^\\x00\\s\\|\\>\\<\\*\\?\\\"]+\\.exe", Pattern.CASE_INSENSITIVE);
        Matcher exeMatcher = exePattern.matcher(content);
        while (exeMatcher.find()) {
            String candidate = exeMatcher.group();
            if (isValidPath(candidate)) {
                candidates.add(candidate);
            }
        }
        
        // Pattern for Windows paths without .exe (fallback)
        Pattern pathPattern = Pattern.compile("[A-Z]:\\\\[^\\x00\\s\\|\\>\\<\\*\\?\\\"]+", Pattern.CASE_INSENSITIVE);
        Matcher pathMatcher = pathPattern.matcher(content);
        while (pathMatcher.find()) {
            String candidate = pathMatcher.group();
            if (isValidPath(candidate) && !candidates.contains(candidate)) {
                candidates.add(candidate);
            }
        }
        
        return candidates;
    }

    // Analyze LNK file structure directly
    private List<String> analyzeLnkStructure(byte[] buffer) {
        List<String> candidates = new ArrayList<>();
        
        try {
            // LNK files have a specific structure. Look for the path in the data
            for (int i = 0; i < buffer.length - 10; i++) {
                // Look for drive letter pattern (C:\ etc)
                if (buffer[i] >= 'A' && buffer[i] <= 'Z' && 
                    buffer[i+1] == ':' && 
                    (buffer[i+2] == '\\' || buffer[i+2] == '/')) {
                    
                    // Extract potential path
                    StringBuilder path = new StringBuilder();
                    path.append((char) buffer[i]).append(":\\");
                    
                    int j = i + 3;
                    while (j < buffer.length && buffer[j] != 0 && buffer[j] != ' ' && 
                           buffer[j] != '\n' && buffer[j] != '\r' && path.length() < 260) {
                        path.append((char) buffer[j]);
                        j++;
                    }
                    
                    String candidate = path.toString();
                    if (candidate.toLowerCase().contains(".exe") && isValidPath(candidate)) {
                        candidates.add(candidate);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return candidates;
    }

    // Select the best path from candidates
    private String selectBestPath(List<String> candidates, String lnkName) {
        if (candidates.isEmpty()) return null;
        
        // Prefer paths with .exe
        for (String candidate : candidates) {
            if (candidate.toLowerCase().endsWith(".exe")) {
                System.out.println("Selected EXE path: " + candidate);
                return candidate;
            }
        }
        
        // Otherwise, take the longest path
        String longest = Collections.max(candidates, Comparator.comparing(String::length));
        System.out.println("Selected longest path: " + longest);
        return longest;
    }

    // Check if path looks valid
    private boolean isValidPath(String path) {
        return path != null && 
               path.length() > 5 && 
               !path.contains("@@") && 
               !path.contains("..") &&
               path.matches("^[A-Z]:\\\\[^\\x00\\s\\|\\>\\<\\*\\?\\\"]+");
    }

    // Parse fields from .desktop file
    private Map<String, String> parseDesktopFile(File file) {
        Map<String, String> map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.contains("=")) {
                    String[] parts = line.split("=", 2);
                    map.put(parts[0].trim(), parts[1].trim());
                }
            }
        } catch (IOException e) {
            Toast.makeText(mContext, mContext.getString(R.string.error_reading_file, file.getName()), Toast.LENGTH_SHORT).show();
        }
        return map;
    }

    // Show detailed error dialog
    private void showErrorDialog(String title, String message) {
        new AlertDialog.Builder(mContext)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .setNeutralButton(R.string.view_logs, (d, w) -> {
                    // Option to view logs in logcat
                    Toast.makeText(mContext, R.string.check_logcat, Toast.LENGTH_LONG).show();
                })
                .show();
    }

    // Confirm deletion of shortcut - UPDATED to also delete settings
    private void confirmDelete(File file, GridLayout grid, String appName) {
        new AlertDialog.Builder(mContext)
                .setTitle(R.string.delete_shortcut)
                .setMessage(mContext.getString(R.string.delete_confirmation, file.getName()))
                .setPositiveButton(R.string.delete, (d, w) -> {
                    if (file.delete()) {
                        // Also delete associated LNK file, icon, and settings
                        String baseName = file.getName().replace(".desktop", "");
                        File lnkFile = new File(desktopDir, baseName + ".lnk");
                        if (lnkFile.exists()) {
                            lnkFile.delete();
                        }
                        
                        File iconFile = new File(iconDir, baseName + ".png");
                        if (iconFile.exists()) {
                            iconFile.delete();
                        }
                        
                        // Delete app settings from SharedPreferences
                        deleteAppSettings(appName);
                        
                        Toast.makeText(mContext, R.string.deleted, Toast.LENGTH_SHORT).show();
                        displayDesktopShortcuts(grid); // refresh
                    } else {
                        Toast.makeText(mContext, R.string.delete_failed, Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }
}

// ProgressDialog class
class ProgressDialog extends AlertDialog {
    private TextView messageView;
    
    protected ProgressDialog(Context context) {
        super(context);
        View view = LayoutInflater.from(context).inflate(R.layout.progress_dialog, null);
        messageView = view.findViewById(R.id.progress_message);
        setView(view);
        setCancelable(false);
    }
    
    public void setMessage(String message) {
        if (messageView != null) {
            messageView.setText(message);
        }
    }
}