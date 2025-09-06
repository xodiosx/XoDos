package com.termux.app;

import java.io.IOException;
import java.lang.reflect.Method;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import android.util.Log;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.termux.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class XoDosWizard {
    private final Activity mActivity;
    private static final String USR_PREFIX = "/data/data/com.termux/files/usr";
    private static final String HOME_PREFIX = "/data/data/com.termux/files/home";
    private static final String PREF_FILE = "com.termux_preferences";
//private final Activity mTermuxActivity;
    
    // runtime fields
    private String androidChoice;
    private String cpuChoice;
    private String wineChoice;
    private String dxvkChoice;
    private String driverChoice;
    private String primaryCores = null;
    private String secondaryCores = null;
    private String winePrefixFromConf = null;

    // UI elements
    private Spinner wineSpinner;
    private Spinner dxvkSpinner;
    private Spinner driverSpinner;
    private Spinner coresSpinner;

    public XoDosWizard(Activity activity) {
        this.mActivity = activity;
    }

    public void start() {
   //     detectAndroidVersion();
        showUnifiedDialog();
    }

    private void detectAndroidVersion(TextView androidVersionLabel) {
    int sdkInt = android.os.Build.VERSION.SDK_INT;         // e.g. 30
    String release = android.os.Build.VERSION.RELEASE;     // e.g. "11"

    // Show real Android version in the TextView
    androidVersionLabel.setText("Android " + release + " (SDK " + sdkInt + ")");

    // Also update your choice logic
    if (sdkInt >= 30) {
        androidChoice = "above11";
        ShowPhantomKillerWarning();   
    } else {
        androidChoice = "under 11";
    }
}

private void ShowPhantomKillerWarning() {
    SharedPreferences prefs = mActivity.getSharedPreferences(PREF_FILE, Activity.MODE_PRIVATE);
    boolean shown = prefs.getBoolean("phantom_warning_shown", false);

    if (!shown) {
        new AlertDialog.Builder(mActivity)
            .setTitle("Disable 🚫 Phantom Process Killer")
            .setMessage("⚠️ Android 11+ may kill background processes.\n\n" +
                        "Follow the guide below to disable the Phantom Process Killer using one of the links.")
            .setPositiveButton("GitHub", (dialog, which) -> {
                openUrl("https://github.com/xodiosx/XoDos");
            })
            .setNeutralButton("Telegram", (dialog, which) -> {
                openUrl("https://t.me/xodemulatorr/3331");
            })
            .setNegativeButton("YouTube🖥️", (dialog, which) -> {
                openUrl("https://youtube.com/shorts/5vOUHn_qvis?si=uMjNpyOVPC6PmPP7");
            })
            .show();

        // Save so it never shows again
        prefs.edit().putBoolean("phantom_warning_shown", true).apply();
    }
}

private void openUrl(String url) {
    try {
        mActivity.startActivity(new android.content.Intent(
            android.content.Intent.ACTION_VIEW,
            android.net.Uri.parse(url)
        ));
    } catch (Exception e) {
        Toast.makeText(mActivity, "Failed to open browser: " + e.getMessage(),
                       Toast.LENGTH_LONG).show();
    }
}

    private void detectCpu() {
        cpuChoice = "other";
        
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/cpuinfo"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains("hardware") || 
                    line.toLowerCase().contains("implementer") ||
                    line.toLowerCase().contains("vendor")) {
                    
                    if (line.toLowerCase().contains("qualcomm") || 
                        line.toLowerCase().contains("snapdragon")) {
                        cpuChoice = "snapdragon";
                        return;
                    } else if (line.toLowerCase().contains("exynos")) {
                        cpuChoice = "exynos";
                        return;
                    } else if (line.toLowerCase().contains("mediatek")) {
                        cpuChoice = "mediatek";
                        return;
                    }
                }
            }
        } catch (IOException ignored) {}
        
        try (BufferedReader br = new BufferedReader(new FileReader("/proc/device-tree/model"))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.toLowerCase().contains("qualcomm") || 
                    line.toLowerCase().contains("snapdragon")) {
                    cpuChoice = "snapdragon";
                    return;
                }
            }
        } catch (IOException ignored) {}
        
        String arch = System.getProperty("os.arch", "");
      /*  if (arch.contains("aarch64") || arch.contains("arm64")) {
            cpuChoice = "arm64";
        } else if (arch.contains("arm")) {
            cpuChoice = "arm";
        } else {
            cpuChoice = "other";
        }
        */
    }

    private void showUnifiedDialog() {
        detectCpu();
        LayoutInflater inflater = mActivity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.wizard_dialog, null);

        TextView androidVersionLabel = dialogView.findViewById(R.id.androidVersionLabel);
detectAndroidVersion(androidVersionLabel);
        TextView cpuLabel = dialogView.findViewById(R.id.cpuLabel);
        wineSpinner = dialogView.findViewById(R.id.wineSpinner);
        dxvkSpinner = dialogView.findViewById(R.id.dxvkSpinner);
        driverSpinner = dialogView.findViewById(R.id.driverSpinner);
        coresSpinner = dialogView.findViewById(R.id.coresSpinner);

    /*    androidVersionLabel.setText(androidChoice.equals("above11") ? 
            "Modern Android (11+)" : "Legacy Android (under 11)");
       */
         cpuLabel.setText(cpuChoice);

        SharedPreferences prefs = mActivity.getSharedPreferences(PREF_FILE, Activity.MODE_PRIVATE);
        
        // Load saved preferences
        wineChoice = prefs.getString("WINE_TYPE", "glibc");
        dxvkChoice = prefs.getString("DXVK_FILE", "<none>");
        driverChoice = prefs.getString("DRIVER_FILE", "<none>");
        primaryCores = prefs.getString("PRIMARY_CORES", null);
        secondaryCores = prefs.getString("SECONDARY_CORES", null);

        // Load wine prefix if glibc is selected
        if ("glibc".equalsIgnoreCase(wineChoice)) {
            loadWinePathConf();
        }

        // Setup wine spinner
        String[] wineOptions = {"glibc", "bionic"};
        ArrayAdapter<String> wineAdapter = new ArrayAdapter<>(mActivity, 
            android.R.layout.simple_spinner_dropdown_item, wineOptions);
        wineSpinner.setAdapter(wineAdapter);
        setSpinnerSelection(wineSpinner, wineChoice);

        // Setup CPU cores spinner
        String[] coreOptions = {
            "No cores", 
            "cores 1 only (6-7)", 
            "cores 2 only (5-7)", 
            "cores 3 only (4-7)", 
            "cores 4 only (3-7)", 
            "cores 6 only (2-7)", 
            "cores 6 only (1-7)", 
            "cores 7 only (0-7)"
        };
        
        ArrayAdapter<String> coresAdapter = new ArrayAdapter<>(mActivity, 
            android.R.layout.simple_spinner_dropdown_item, coreOptions);
        coresSpinner.setAdapter(coresAdapter);
        int coreSelection = getCoreSelectionIndex(primaryCores, secondaryCores);
        coresSpinner.setSelection(coreSelection);

        // Load DXVK and driver lists after a short delay to ensure UI is ready
        dialogView.post(new Runnable() {
            @Override
            public void run() {
                loadDxvkListForWine(wineChoice, dxvkChoice);
                loadDriverListForWine(wineChoice, driverChoice);
            }
        });

        wineSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                String newWine = wineOptions[position];
                wineChoice = newWine;
                if ("glibc".equalsIgnoreCase(newWine)) {
                    loadWinePathConf();
                }
                loadDxvkListForWine(newWine, "<none>");
                loadDriverListForWine(newWine, "<none>");
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {}
        });

        new AlertDialog.Builder(mActivity)
            .setTitle("XoDos Wine Settings")
            .setView(dialogView)
            .setPositiveButton("OK", (dialog, which) -> {
                wineChoice = wineSpinner.getSelectedItem().toString();
                dxvkChoice = dxvkSpinner.getSelectedItem().toString();
                driverChoice = driverSpinner.getSelectedItem().toString();
                int chosenCoresIndex = coresSpinner.getSelectedItemPosition();
                String previousDriver = prefs.getString("DRIVER_FILE", "<none>");

                // Map core selection to primary and secondary cores
                switch (chosenCoresIndex) {
                    case 0:
                        primaryCores = null;
                        secondaryCores = null;
                        break;
                    case 1:
                        primaryCores = "6-7";
                        secondaryCores = "0-5";
                        break;
                    case 2:
                        primaryCores = "5-7";
                        secondaryCores = "0-4";
                        break;
                    case 3:
                        primaryCores = "4-7";
                        secondaryCores = "0-3";
                        break;
                    case 4:
                        primaryCores = "3-7";
                        secondaryCores = "0-2";
                        break;
                    case 5:
                        primaryCores = "2-7";
                        secondaryCores = "0-1";
                        break;
                    case 6:
                        primaryCores = "1-7";
                        secondaryCores = "0-1";
                        break;
                    case 7:
                        primaryCores = "0-7";
                        secondaryCores = "0-1";
                        break;
                }

                // Save all preferences
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString("WINE_TYPE", wineChoice);
                editor.putString("DXVK_FILE", dxvkChoice);
                editor.putString("DRIVER_FILE", driverChoice);
                editor.putString("PRIMARY_CORES", primaryCores);
                editor.putString("SECONDARY_CORES", secondaryCores);
                editor.apply();

                saveConfigFile();

                if (requiresDriverRestart(previousDriver, driverChoice)) {
                    new AlertDialog.Builder(mActivity)
                        .setTitle("Restart Required")
                        .setMessage("⚠️Changing drivers requires server and restarting Wine. Continue?")
                        .setPositiveButton("OK", (d, w) -> {
                            extractArchives();
                            performRestartSequence();
                        })
                        .setNegativeButton("Cancel", (d, w) -> {
                            extractArchives();
                        })
                        .show();
                } else {
                    extractArchives();
                }
                
                Toast.makeText(mActivity, "Settings saved", Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Cancel", null)
            .show();
    }

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

    private int getCoreSelectionIndex(String primary, String secondary) {
        if (primary == null && secondary == null) return 0;
        if ("6-7".equals(primary) && "0-5".equals(secondary)) return 1;
        if ("5-7".equals(primary) && "0-4".equals(secondary)) return 2;
        if ("4-7".equals(primary) && "0-3".equals(secondary)) return 3;
        if ("3-7".equals(primary) && "0-2".equals(secondary)) return 4;
        if ("2-7".equals(primary) && "0-1".equals(secondary)) return 5;
        if ("1-7".equals(primary) && "0-1".equals(secondary)) return 6;
        if ("0-7".equals(primary) && "0-1".equals(secondary)) return 7;
        return 0;
    }

    private boolean requiresDriverRestart(String oldDriver, String newDriver) {
        if (oldDriver == null || newDriver == null) return false;
        boolean oldIsTurnip = oldDriver.toLowerCase().contains("turnip");
        boolean newIsTurnip = newDriver.toLowerCase().contains("turnip");
        boolean oldIsVirgl = oldDriver.toLowerCase().contains("virgl");
        boolean newIsVirgl = newDriver.toLowerCase().contains("virgl");
        return (oldIsTurnip && newIsVirgl) || (oldIsVirgl && newIsTurnip);
    }

    private void extractArchives() {
        if (!"<none>".equals(dxvkChoice)) {
            String dxvkSrc = USR_PREFIX + "/glibc/opt/libs/d3d/" + dxvkChoice;
            String winePrefix = "glibc".equals(wineChoice) ? 
                (winePrefixFromConf != null ? winePrefixFromConf : HOME_PREFIX + "/.wine") : 
                HOME_PREFIX + "/.wine";
            String dxvkExtractPath = winePrefix + "/drive_c/windows";
            execShell(USR_PREFIX + "/bin/7z x '" + dxvkSrc + "' -o'" + dxvkExtractPath + "' -y");
        }

        if (!"<none>".equals(driverChoice)) {
            String driverSrc;
            String driverExtractPath;
            if ("glibc".equals(wineChoice)) {
                driverSrc = USR_PREFIX + "/glibc/opt/libs/mesa/" + driverChoice;
                driverExtractPath = USR_PREFIX + "/glibc";
            } else {
                driverSrc = USR_PREFIX + "/drivers/25/" + driverChoice;
                driverExtractPath = USR_PREFIX + "/drivers/mesa-242";
            }
            execShell(USR_PREFIX + "/bin/7z x '" + driverSrc + "' -o'" + driverExtractPath + "' -y");
        }
    }

    private void execShell(String cmd) {
    try {
        Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", cmd});
        
        // Capture output and error streams
        BufferedReader stdInput = new BufferedReader(new 
            InputStreamReader(process.getInputStream()));
        BufferedReader stdError = new BufferedReader(new 
            InputStreamReader(process.getErrorStream()));
        
        String s;
        StringBuilder output = new StringBuilder();
        StringBuilder error = new StringBuilder();
        
        while ((s = stdInput.readLine()) != null) {
            output.append(s).append("\n");
        }
        
        while ((s = stdError.readLine()) != null) {
            error.append(s).append("\n");
        }
        
        process.waitFor();
        
        // Log the results
        Log.d("ShellCommand", "Output: " + output.toString());
      //  if (error.length() > 0) {
          //  Log.e("ShellCommand", "Error: " + error.toString());
         //   Toast.makeText(this, "Command error: " + error.toString(), 
         //       Toast.LENGTH_LONG).show();
     //   }
        
    } catch (IOException | InterruptedException e) {
        e.printStackTrace();
        
    }
}


private void performRestartSequence() {
    // First, try a simple command to see if execution works
    execShell("echo ' stopping wine execution'");
        
   // Try each command separately
    String[] commands = {
        USR_PREFIX + "/glibc/bin/box64 " + USR_PREFIX + "/glibc/bin/wineserver -k",
        USR_PREFIX + "/bin/box64 " + USR_PREFIX + "/opt/wine/bin/wineserver -k",
        "pkill -f \"termux.x11\""  // Alternative to kill -9 $(pgrep -f)
    };
    
    for (String cmd : commands) {
        execShell(cmd);
    }
    Toast.makeText(mActivity, "Wine processes stopped", Toast.LENGTH_SHORT).show();
}



    private void saveConfigFile() {
        try {
            String dxvkSrc = USR_PREFIX + "/glibc/opt/libs/d3d/" + dxvkChoice;
            String driverSrc;
            String winePrefix;
            String dxvkExtractPath;
            String driverExtractPath;

            if ("glibc".equals(wineChoice)) {
                winePrefix = winePrefixFromConf != null ? winePrefixFromConf : "$WINEPREFIX";
                dxvkExtractPath = winePrefix + "/drive_c/windows";
                driverSrc = USR_PREFIX + "/glibc/opt/libs/mesa/" + driverChoice;
                driverExtractPath = USR_PREFIX + "/glibc";
            } else {
                winePrefix = HOME_PREFIX + "/.wine";
                dxvkExtractPath = winePrefix + "/drive_c/windows";
                driverSrc = USR_PREFIX + "/drivers/25/" + driverChoice;
                driverExtractPath = USR_PREFIX + "/drivers/mesa-242";
            }

            FileWriter w = new FileWriter(HOME_PREFIX + "/xodwine.cfg");
            w.write("ANDROID_VERSION=" + androidChoice + "\n");
            w.write("CPU=" + cpuChoice + "\n");
            w.write("WINE_TYPE=" + wineChoice + "\n");
            w.write("DXVK_FILE=" + dxvkChoice + "\n");
            w.write("DXVK_SRC=" + dxvkSrc + "\n");
            w.write("DXVK_EXTRACT_PATH=" + dxvkExtractPath + "\n");
            w.write("DRIVER_FILE=" + driverChoice + "\n");
            w.write("DRIVER_SRC=" + driverSrc + "\n");
            w.write("DRIVER_EXTRACT_PATH=" + driverExtractPath + "\n");
            w.write("WINEPREFIX=" + winePrefix + "\n");

            if (primaryCores == null || secondaryCores == null) {
                w.write("unset PRIMARY_CORES\n");
                w.write("unset SECONDARY_CORES\n");
            } else {
                w.write("PRIMARY_CORES=" + primaryCores + "\n");
                w.write("SECONDARY_CORES=" + secondaryCores + "\n");
            }

            if ("glibc".equals(wineChoice)) {
                w.write("SOURCE_CONFIG=1\n");
                w.write("CONFIG_SCRIPT=" + USR_PREFIX + "/glibc/opt/scripts/configs\n");
            } else {
                w.write("SOURCE_CONFIG=0\n");
            }

            w.close();
        } catch (IOException e) {
            Toast.makeText(mActivity, "Error saving config: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
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
            Toast.makeText(mActivity, "Error reading wine config: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void loadDxvkListForWine(String wineType, String selectIfFound) {
        File dxvkDir = new File(USR_PREFIX + "/glibc/opt/libs/d3d");
        List<String> list = listArchiveFiles(dxvkDir);
        updateSpinner(dxvkSpinner, list, selectIfFound);
    }

    private void loadDriverListForWine(String wineType, String selectIfFound) {
        File driverDir;
        if ("glibc".equals(wineType)) {
            driverDir = new File(USR_PREFIX + "/glibc/opt/libs/mesa");
        } else {
            driverDir = new File(USR_PREFIX + "/drivers/25");
        }
        List<String> list = listArchiveFiles(driverDir);
        updateSpinner(driverSpinner, list, selectIfFound);
    }

    private List<String> listArchiveFiles(File dir) {
        List<String> files = new ArrayList<>();
        if (dir.exists() && dir.isDirectory()) {
            for (File f : dir.listFiles()) {
                if (f.getName().endsWith(".7z") || f.getName().endsWith(".tar.gz")) {
                    files.add(f.getName());
                }
            }
        }
        if (files.isEmpty()) files.add("<none>");
        return files;
    }

    private void updateSpinner(Spinner spinner, List<String> items, String selectIfFound) {
        ArrayAdapter<String> adapter = new ArrayAdapter<>(mActivity, 
            android.R.layout.simple_spinner_dropdown_item, items);
        spinner.setAdapter(adapter);
        
        // Set selection after a short delay to ensure adapter is ready
        spinner.post(new Runnable() {
            @Override
            public void run() {
                setSpinnerSelection(spinner, selectIfFound);
            }
        });
    }
}