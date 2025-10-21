package com.termux.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;
import android.util.Log;

// Add these imports for terminal session and Java IO
import com.termux.terminal.TerminalSession;
import com.termux.view.TerminalView;
import com.termux.R;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class XoDosFileManager {

    private Context mContext;
    private File currentDir;
    private ListView fileListView;
    private AlertDialog dialog;
    private FileAdapter fileAdapter;
    private TextView pathTextView;
    private Button pasteButton; // Reference to paste button
    private Button selectButton; // Reference to select button

    private final String TERMUX_HOME = "/data/data/com.termux/files/home";
    private final String TERMUX_DATA = "/data/data/com.termux";
    private final File DESKTOP_DIR = new File(TERMUX_HOME + "/Desktop/shortcuts");
    private final File ICON_DIR = new File(TERMUX_HOME + "/ico");

    // Track which EXE files are currently generating icons
    private Set<String> generatingIcons = new HashSet<>();

    // Android-style icon mappings
    private Map<String, Integer> iconMap = new HashMap<>();

    // Copy functionality only (no cut)
    private File copiedFile = null;

    // SharedPreferences for saving last path
    private SharedPreferences sharedPreferences;
    private static final String PREFS_NAME = "XoDosFileManagerPrefs";
    private static final String LAST_PATH_KEY = "last_visited_path";

    // Selection mode
    private boolean isSelectionMode = false;
    private List<File> selectedFiles = new ArrayList<>();

    public XoDosFileManager(Context context) {
        this.mContext = context;
        this.sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        
        // Load last visited path or default to Downloads
        String lastPath = sharedPreferences.getString(LAST_PATH_KEY, TERMUX_HOME + "/Downloads");
        this.currentDir = new File(lastPath);
        if (!currentDir.exists() || !currentDir.isDirectory()) {
            this.currentDir = new File(TERMUX_HOME + "/Downloads");
        }
        
        // Initialize Android-style icon mappings
        initializeAndroidIcons();
        
        // Create necessary directories
        if (!DESKTOP_DIR.exists()) DESKTOP_DIR.mkdirs();
        if (!ICON_DIR.exists()) ICON_DIR.mkdirs();
    }

    private void initializeAndroidIcons() {
        // Use standard Android icons for file types
        iconMap.put("folder", R.drawable.ifm);
        iconMap.put("parent", android.R.drawable.ic_menu_revert);
        
        // Standard Android file type icons
        iconMap.put("exe", android.R.drawable.ic_media_play);
        iconMap.put("iso", R.drawable.idsc);
        iconMap.put("xiso", R.drawable.ixbox); // Changed to Xbox icon for xiso files
        iconMap.put("xbe", R.drawable.idsc);
        iconMap.put("cue", R.drawable.idsc);
        iconMap.put("bin", R.drawable.idsc);
        iconMap.put("cso", R.drawable.idsc);
        iconMap.put("disk_image", R.drawable.idsc);
        iconMap.put("text", android.R.drawable.ic_menu_edit);
        iconMap.put("image", android.R.drawable.ic_menu_gallery);
        iconMap.put("audio", android.R.drawable.ic_media_ff);
        iconMap.put("video", android.R.drawable.ic_media_play);
        iconMap.put("pdf", android.R.drawable.ic_menu_save);
        iconMap.put("archive", R.drawable.irestore);
        iconMap.put("default", android.R.drawable.ic_menu_help);
        iconMap.put("script", R.drawable.iscript);
        iconMap.put("config", android.R.drawable.ic_menu_preferences);
        iconMap.put("xbox", R.drawable.ixbox);
        
        // New icons for additional file types
        iconMap.put("deb", R.drawable.irestore); // Use archive icon for DEB files
        iconMap.put("document", android.R.drawable.ic_menu_edit);
        iconMap.put("spreadsheet", android.R.drawable.ic_menu_edit);
        iconMap.put("presentation", android.R.drawable.ic_menu_edit);
        iconMap.put("database", android.R.drawable.ic_menu_save);
        iconMap.put("font", android.R.drawable.ic_menu_edit);
        iconMap.put("web", android.R.drawable.ic_menu_edit);
        iconMap.put("programming", android.R.drawable.ic_menu_edit);
    }

    public void start() {
        showFileManager();
    }

    /** Save current path to SharedPreferences */
    private void saveCurrentPath() {
        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.putString(LAST_PATH_KEY, currentDir.getAbsolutePath());
        editor.apply();
    }

    /** Navigate back one level */
    private void navigateBack() {
        File parent = currentDir.getParentFile();
        if (parent != null && parent.exists()) {
            loadDirectory(parent);
        } else {
            // If we're at the root, close the file manager
            if (dialog != null && dialog.isShowing()) {
                dialog.dismiss();
            }
        }
    }

    /** Main UI setup with ListView **/
    private void showFileManager() {
        // Create layout programmatically
        LinearLayout mainLayout = new LinearLayout(mContext);
        mainLayout.setOrientation(LinearLayout.VERTICAL);
        mainLayout.setLayoutParams(new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.MATCH_PARENT
        ));
        mainLayout.setBackgroundColor(0xFF333333); // Dark background
        
        // Add path display
        pathTextView = new TextView(mContext);
        pathTextView.setText(currentDir.getAbsolutePath());
        pathTextView.setTextColor(0xFFFFFFFF); // White text
        pathTextView.setPadding(20, 10, 20, 10);
        pathTextView.setSingleLine(true);
        pathTextView.setEllipsize(android.text.TextUtils.TruncateAt.START);
        pathTextView.setBackgroundColor(0xFF444444);
        mainLayout.addView(pathTextView);
        
        // Navigation bar
        LinearLayout navLayout = createNavigationBar();
        mainLayout.addView(navLayout);
        
        // ListView for files
        fileListView = new ListView(mContext);
        fileListView.setBackgroundColor(0xFF333333); // Dark background
        mainLayout.addView(fileListView, new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, 
            LinearLayout.LayoutParams.MATCH_PARENT
        ));

        dialog = new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.file_manager_title))
                .setView(mainLayout)
                .setNegativeButton(mContext.getString(R.string.close), (d, w) -> {
                    saveCurrentPath(); // Save path when closing
                    d.dismiss();
                })
                .create();

        // Set back button listener for the dialog
        dialog.setOnKeyListener((dialog, keyCode, event) -> {
            if (keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_UP) {
                navigateBack();
                return true; // Consume the event
            }
            return false;
        });

        dialog.show();
        loadDirectory(currentDir);
    }

    private LinearLayout createNavigationBar() {
        LinearLayout navLayout = new LinearLayout(mContext);
        navLayout.setOrientation(LinearLayout.HORIZONTAL);
        navLayout.setPadding(10, 5, 10, 5);
        navLayout.setBackgroundColor(0xFF444444);

        // Back button
        Button backBtn = new Button(mContext);
        backBtn.setText(mContext.getString(R.string.back));
        backBtn.setTextColor(0xFFFFFFFF);
        backBtn.setTextSize(12);
        backBtn.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backParams.weight = 1;
        backBtn.setLayoutParams(backParams);
        backBtn.setOnClickListener(v -> navigateBack());

        // Home button
        Button homeBtn = new Button(mContext);
        homeBtn.setText(mContext.getString(R.string.home));
        homeBtn.setTextColor(0xFFFFFFFF);
        homeBtn.setTextSize(12);
        homeBtn.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        homeParams.weight = 1;
        homeBtn.setLayoutParams(homeParams);
        homeBtn.setOnClickListener(v -> goHome());

        // Select button
        selectButton = new Button(mContext);
        selectButton.setText(mContext.getString(R.string.select_mode_off));
        selectButton.setTextColor(0xFFFFFFFF);
        selectButton.setTextSize(10);
        selectButton.setPadding(4, 8, 4, 8);
        LinearLayout.LayoutParams selectParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        selectParams.weight = 0.5f;
        selectButton.setLayoutParams(selectParams);
        selectButton.setOnClickListener(v -> toggleSelectionMode());

        // Paste button
        pasteButton = new Button(mContext);
        pasteButton.setText(mContext.getString(R.string.paste));
        pasteButton.setTextColor(0xFFFFFFFF);
        pasteButton.setTextSize(10);
        pasteButton.setPadding(4, 8, 4, 8);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pasteParams.weight = 0.5f;
        pasteButton.setLayoutParams(pasteParams);
        pasteButton.setEnabled(copiedFile != null);
        pasteButton.setOnClickListener(v -> pasteFile());

        // Help button
        Button helpBtn = new Button(mContext);
        helpBtn.setText(mContext.getString(R.string.help));
        helpBtn.setTextColor(0xFFFFFFFF);
        helpBtn.setTextSize(12);
        helpBtn.setPadding(8, 8, 8, 8);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        helpParams.weight = 1;
        helpBtn.setLayoutParams(helpParams);
        helpBtn.setOnClickListener(v -> showHelpDialog());

        navLayout.addView(backBtn);
        navLayout.addView(homeBtn);
        navLayout.addView(selectButton);
        navLayout.addView(pasteButton);
        navLayout.addView(helpBtn);

        return navLayout;
    }

    /** Toggle selection mode for multiple files */
    private void toggleSelectionMode() {
        isSelectionMode = !isSelectionMode;
        selectedFiles.clear();
        
        if (isSelectionMode) {
            selectButton.setText(mContext.getString(R.string.select_mode_on));
            selectButton.setBackgroundColor(0xFF4CAF50); // Green background when active
            Toast.makeText(mContext, mContext.getString(R.string.select_mode_on), Toast.LENGTH_SHORT).show();
        } else {
            selectButton.setText(mContext.getString(R.string.select_mode_off));
            selectButton.setBackgroundColor(0xFF444444); // Default background
            Toast.makeText(mContext, mContext.getString(R.string.select_mode_off), Toast.LENGTH_SHORT).show();
        }
        
        if (fileAdapter != null) {
            fileAdapter.notifyDataSetChanged();
        }
    }

    /** Show options for selected files */
    private void showSelectedFilesOptions() {
        if (selectedFiles.isEmpty()) {
            Toast.makeText(mContext, mContext.getString(R.string.no_files_selected), Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if we have any folders for packing
        boolean hasFolders = false;
        for (File file : selectedFiles) {
            if (file.isDirectory()) {
                hasFolders = true;
                break;
            }
        }

        List<String> optionsList = new ArrayList<>();
        optionsList.add("📦 " + mContext.getString(R.string.pack_selected));
        optionsList.add("📋 " + mContext.getString(R.string.copy_selected));
        optionsList.add("🔒 " + mContext.getString(R.string.change_permissions));
        optionsList.add("❌ " + mContext.getString(R.string.clear_selection));
        
        String[] options = optionsList.toArray(new String[0]);
        
        new AlertDialog.Builder(mContext)
                .setTitle(String.format(mContext.getString(R.string.selected_files2), selectedFiles.size()))
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // Pack with xdvdfs
                            packSelectedFiles();
                            break;
                        case 1: // Copy selected
                            copySelectedFiles();
                            break;
                        case 2: // Change permissions
                            changePermissionsForSelectedFiles();
                            break;
                        case 3: // Clear Selection
                            selectedFiles.clear();
                            isSelectionMode = false;
                            selectButton.setText(mContext.getString(R.string.select_mode_off));
                            selectButton.setBackgroundColor(0xFF444444);
                            fileAdapter.notifyDataSetChanged();
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Pack selected files using xdvdfs - only for folders */
    private void packSelectedFiles() {
        if (selectedFiles.isEmpty()) return;

        // Find first directory in selection
        File folderToPack = null;
        for (File file : selectedFiles) {
            if (file.isDirectory()) {
                folderToPack = file;
                break;
            }
        }

        if (folderToPack != null) {
            String outputName = folderToPack.getName() + ".xiso";
            File outputFile = new File(currentDir, outputName);
            
            String command = "xdvdfs pack \"" + folderToPack.getAbsolutePath() + "\" \"" + outputFile.getAbsolutePath() + "\"";
            runCommandInSession(command, String.format(mContext.getString(R.string.packing_directory), outputName));
        } else {
            // Also allow packing ISO files to XISO
            File isoToPack = null;
            for (File file : selectedFiles) {
                if (file.isFile() && (file.getName().toLowerCase().endsWith(".iso") || 
                    file.getName().toLowerCase().endsWith(".xiso"))) {
                    isoToPack = file;
                    break;
                }
            }
            
            if (isoToPack != null) {
                String baseName = isoToPack.getName().replaceFirst("\\.[^.]+$", "");
                String outputName = baseName + ".xiso";
                File outputFile = new File(currentDir, outputName);
                
                String command = "xdvdfs pack \"" + isoToPack.getAbsolutePath() + "\" \"" + outputFile.getAbsolutePath() + "\"";
                runCommandInSession(command, String.format(mContext.getString(R.string.packing_directory), outputName));
            } else {
                Toast.makeText(mContext, mContext.getString(R.string.select_directory_pack), Toast.LENGTH_SHORT).show();
            }
        }
        
        // Clear selection after operation
        selectedFiles.clear();
        isSelectionMode = false;
        selectButton.setText(mContext.getString(R.string.select_mode_off));
        selectButton.setBackgroundColor(0xFF444444);
        fileAdapter.notifyDataSetChanged();
    }

    /** Copy selected files */
    private void copySelectedFiles() {
        if (selectedFiles.isEmpty()) return;
        
        // For multiple selection, we'll copy the first file for now
        // In a more advanced version, you could handle multiple files
        copiedFile = selectedFiles.get(0);
        refreshPasteButton();
        
        Toast.makeText(mContext, 
            mContext.getString(R.string.copied) + ": " + copiedFile.getName(), 
            Toast.LENGTH_SHORT).show();
            
        // Clear selection after operation
        selectedFiles.clear();
        isSelectionMode = false;
        selectButton.setText(mContext.getString(R.string.select_mode_off));
        selectButton.setBackgroundColor(0xFF444444);
        fileAdapter.notifyDataSetChanged();
    }

    /** Show help dialog with supported file types **/
    private void showHelpDialog() {
        String helpMessage = mContext.getString(R.string.help_message) + "\n\n" +
                            mContext.getString(R.string.file_types) + ":\n" +
                            "• " + mContext.getString(R.string.deb_files) + "\n" +
                            "• " + mContext.getString(R.string.script_files) + "\n" +
                            "• " + mContext.getString(R.string.audio_files) + "\n" +
                            "• " + mContext.getString(R.string.document_files) + "\n" +
                            "• " + mContext.getString(R.string.spreadsheet_files) + "\n" +
                            "• " + mContext.getString(R.string.presentation_files) + "\n" +
                            "• " + mContext.getString(R.string.database_files) + "\n" +
                            "• " + mContext.getString(R.string.font_files) + "\n" +
                            "• " + mContext.getString(R.string.web_files) + "\n" +
                            "• " + mContext.getString(R.string.programming_files);

        new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.supported_file_types))
                .setMessage(helpMessage)
                .setPositiveButton(mContext.getString(R.string.ok), null)
                .show();
    }

    /** Load and display folder contents with icons **/
    private void loadDirectory(File dir) {
        if (fileListView == null) return;

        if (dir == null || !dir.exists() || !dir.isDirectory()) {
            Toast.makeText(mContext, mContext.getString(R.string.cannot_open_folder), Toast.LENGTH_SHORT).show();
            return;
        }

        currentDir = dir;
        saveCurrentPath(); // Save path when changing directory
        
        // UPDATE THE PATH TEXT VIEW
        if (pathTextView != null) {
            pathTextView.setText(dir.getAbsolutePath());
        }

        File[] files = dir.listFiles();
        if (files == null) {
            Toast.makeText(mContext, mContext.getString(R.string.cannot_read_folder), Toast.LENGTH_SHORT).show();
            return;
        }

        // Sort: directories first, then files
        Arrays.sort(files, new Comparator<File>() {
            @Override
            public int compare(File f1, File f2) {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            }
        });

        fileAdapter = new FileAdapter(mContext, files);
        fileListView.setAdapter(fileAdapter);
        
        fileListView.setOnItemClickListener((parent, view, position, id) -> {
            File selectedFile = files[position];
            if (isSelectionMode) {
                // Toggle selection
                if (selectedFiles.contains(selectedFile)) {
                    selectedFiles.remove(selectedFile);
                } else {
                    selectedFiles.add(selectedFile);
                }
                fileAdapter.notifyDataSetChanged();
                
                // If we have selections, show options
                if (!selectedFiles.isEmpty()) {
                    showSelectedFilesOptions();
                }
            } else {
                handleItemClick(selectedFile);
            }
        });

        // Add long press listener for copy operation
        fileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            File selectedFile = files[position];
            if (isSelectionMode) {
                // In selection mode, long press toggles selection
                if (selectedFiles.contains(selectedFile)) {
                    selectedFiles.remove(selectedFile);
                } else {
                    selectedFiles.add(selectedFile);
                }
                fileAdapter.notifyDataSetChanged();
                return true;
            } else {
                showCopyOptions(selectedFile);
                return true;
            }
        });

        // Generate icons for EXE files in this directory
        generateExeIconsForDirectory(files);
    }

    /** Show copy options on long press */
    private void showCopyOptions(File file) {
        List<String> optionsList = new ArrayList<>();
        optionsList.add(mContext.getString(R.string.copy));
        
        // Only show pack option for directories or ISO/XISO files
        if (file.isDirectory() || file.getName().toLowerCase().endsWith(".iso") || 
            file.getName().toLowerCase().endsWith(".xiso")) {
            if (file.isDirectory()) {
                optionsList.add("📦 " + mContext.getString(R.string.pack_folder));
            } else {
                optionsList.add("📦 " + mContext.getString(R.string.pack_iso));
            }
        }
        
        optionsList.add("🔒 " + mContext.getString(R.string.change_permissions));
        optionsList.add(mContext.getString(R.string.cancel));
        
        String[] options = optionsList.toArray(new String[0]);
        
        new AlertDialog.Builder(mContext)
                .setTitle(file.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // Copy
                            copiedFile = file;
                            Toast.makeText(mContext, 
                                mContext.getString(R.string.copied) + ": " + file.getName(), 
                                Toast.LENGTH_SHORT).show();
                            refreshPasteButton();
                            break;
                        case 1: // Pack with xdvdfs or Change permissions
                            if (options[1].contains("📦")) {
                                // Pack operation
                                if (file.isDirectory() || file.getName().toLowerCase().endsWith(".iso") || 
                                    file.getName().toLowerCase().endsWith(".xiso")) {
                                    String baseName = file.getName().replaceFirst("\\.[^.]+$", "");
                                    String outputName = baseName + ".xiso";
                                    File outputFile = new File(currentDir, outputName);
                                    String command = "xdvdfs pack \"" + file.getAbsolutePath() + "\" \"" + outputFile.getAbsolutePath() + "\"";
                                    runCommandInSession(command, String.format(mContext.getString(R.string.packing_directory), outputName));
                                }
                            } else {
                                // Change permissions
                                changePermissionsRecursive(file);
                            }
                            break;
                        case 2: // Change permissions or Cancel
                            if (options[2].contains("🔒")) {
                                changePermissionsRecursive(file);
                            }
                            break;
                    }
                })
                .show();
    }

    /** Paste the copied file */
    private void pasteFile() {
        if (copiedFile == null) {
            Toast.makeText(mContext, mContext.getString(R.string.no_file_to_paste), Toast.LENGTH_SHORT).show();
            return;
        }

        // Check if source file still exists
        if (!copiedFile.exists()) {
            Toast.makeText(mContext, mContext.getString(R.string.source_file_no_longer_exists), Toast.LENGTH_SHORT).show();
            copiedFile = null;
            refreshPasteButton();
            return;
        }

        new Thread(() -> {
            try {
                File destination = new File(currentDir, copiedFile.getName());
                
                // Handle file name conflicts
                int counter = 1;
                String originalName = destination.getName();
                String baseName = originalName;
                String extension = "";
                int dotIndex = originalName.lastIndexOf('.');
                if (dotIndex > 0) {
                    baseName = originalName.substring(0, dotIndex);
                    extension = originalName.substring(dotIndex);
                }
                
                while (destination.exists()) {
                    String newName = baseName + " (" + counter + ")" + extension;
                    destination = new File(currentDir, newName);
                    counter++;
                }

                // Copy operation
                if (copyFileOrDirectory(copiedFile, destination)) {
                    ((android.app.Activity) mContext).runOnUiThread(() -> {
                        Toast.makeText(mContext, 
                            mContext.getString(R.string.copied) + ": " + copiedFile.getName(), 
                            Toast.LENGTH_SHORT).show();
                    });
                } else {
                    throw new IOException(mContext.getString(R.string.failed_to_copy_file));
                }

                // Refresh the list
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    loadDirectory(currentDir);
                });

            } catch (Exception e) {
                Log.e("PasteError", "Paste failed: " + e.getMessage());
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, 
                        mContext.getString(R.string.paste_failed) + ": " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** Recursively copy file or directory */
    private boolean copyFileOrDirectory(File source, File destination) {
        try {
            if (source.isDirectory()) {
                if (!destination.exists() && !destination.mkdirs()) {
                    Log.e("FileCopy", "Failed to create directory: " + destination.getAbsolutePath());
                    return false;
                }
                
                String[] children = source.list();
                if (children != null) {
                    for (String child : children) {
                        boolean success = copyFileOrDirectory(
                            new File(source, child),
                            new File(destination, child)
                        );
                        if (!success) {
                            return false;
                        }
                    }
                }
                return true;
            } else {
                // Copy single file
                try (FileInputStream in = new FileInputStream(source);
                     FileOutputStream out = new FileOutputStream(destination)) {
                    
                    byte[] buffer = new byte[8192];
                    int length;
                    while ((length = in.read(buffer)) > 0) {
                        out.write(buffer, 0, length);
                    }
                }
                return true;
            }
        } catch (Exception e) {
            Log.e("FileCopy", "Failed to copy " + source.getName() + ": " + e.getMessage());
            return false;
        }
    }

    /** Refresh paste button state */
    private void refreshPasteButton() {
        if (pasteButton != null) {
            pasteButton.setEnabled(copiedFile != null);
        }
    }

    /** Run xdvdfs command and show output in alert dialog */
    private void runXdvdfsCommandInAlert(File file, String command, String title) {
        new Thread(() -> {
            try {
                String fullCommand = "xdvdfs " + command + " \"" + file.getAbsolutePath() + "\"";
                String output = runCommandAndCaptureOutput(fullCommand);
                
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    showTextDialog(title, output);
                });
                
            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, 
                        "Failed to run command: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** Run command and capture output as string */
    private String runCommandAndCaptureOutput(String cmd) {
        StringBuilder output = new StringBuilder();
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", cmd);
            
            Map<String, String> env = processBuilder.environment();
            env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin");
            env.put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");
            env.put("PREFIX", "/data/data/com.termux/files/usr");
            env.put("HOME", "/data/data/com.termux/files/home");
            env.put("TMPDIR", "/data/data/com.termux/files/usr/tmp");
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                output.append("\nCommand exited with code: ").append(exitCode);
            }
            
        } catch (Exception e) {
            output.append("Error executing command: ").append(e.getMessage());
        }
        return output.toString();
    }

    /** Show text in a scrollable dialog */
    private void showTextDialog(String title, String content) {
        ScrollView scrollView = new ScrollView(mContext);
        TextView textView = new TextView(mContext);
        textView.setText(content);
        textView.setTextColor(0xFFFFFFFF);
        textView.setBackgroundColor(0xFF333333);
        textView.setPadding(20, 20, 20, 20);
        textView.setTextIsSelectable(true);
        
        scrollView.addView(textView);
        
        new AlertDialog.Builder(mContext)
                .setTitle(title)
                .setView(scrollView)
                .setPositiveButton(mContext.getString(R.string.ok), null)
                .show();
    }

    /** Custom adapter for files with icons **/
    private class FileAdapter extends ArrayAdapter<File> {
        private Context context;
        private File[] files;

        public FileAdapter(Context context, File[] files) {
            super(context, 0, files);
            this.context = context;
            this.files = files;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView;
            FileHolder holder;

            if (row == null) {
                // Create row view programmatically
                row = createFileItemView();
                holder = new FileHolder();
                holder.icon = row.findViewById(android.R.id.icon);
                holder.name = row.findViewById(android.R.id.text1);
                holder.size = row.findViewById(android.R.id.text2);
                row.setTag(holder);
            } else {
                holder = (FileHolder) row.getTag();
            }

            File file = files[position];
            holder.name.setText(file.getName());
            
            // Highlight selected files
            if (isSelectionMode && selectedFiles.contains(file)) {
                row.setBackgroundColor(0xFF555555);
            } else {
                row.setBackgroundColor(0xFF333333);
            }
            
            // Set icon based on file type
            setFileIcon(holder.icon, file);
            
            // Set file size or directory indicator
            if (file.isDirectory()) {
                holder.size.setText(mContext.getString(R.string.folder));
            } else {
                holder.size.setText(formatFileSize(file.length()));
            }

            return row;
        }

        private View createFileItemView() {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(30, 20, 30, 20);
            row.setBackgroundColor(0xFF333333);
            row.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));

            // Icon
            ImageView icon = new ImageView(context);
            icon.setId(android.R.id.icon);
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(80, 80);
            iconParams.setMargins(0, 0, 20, 0);
            icon.setLayoutParams(iconParams);
            row.addView(icon);

            // Text container
            LinearLayout textContainer = new LinearLayout(context);
            textContainer.setOrientation(LinearLayout.VERTICAL);
            textContainer.setLayoutParams(new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1
            ));

            // File name
            TextView name = new TextView(context);
            name.setId(android.R.id.text1);
            name.setTextSize(16);
            name.setTextColor(0xFFFFFFFF);
            name.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textContainer.addView(name);

            // File size/type
            TextView size = new TextView(context);
            size.setId(android.R.id.text2);
            size.setTextSize(12);
            size.setTextColor(0xFFCCCCCC);
            size.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textContainer.addView(size);

            row.addView(textContainer);

            return row;
        }

        private void setFileIcon(ImageView iconView, File file) {
            if (file.isDirectory()) {
                iconView.setImageResource(iconMap.get("folder"));
                return;
            }

            String name = file.getName().toLowerCase();

            if (name.endsWith(".exe")) {
                if (!setExeIcon(iconView, file)) {
                    iconView.setImageResource(iconMap.get("exe"));
                }
            } else if (name.endsWith(".xiso")) {
                iconView.setImageResource(iconMap.get("xiso")); // Use Xbox icon for xiso files
            } else if (name.endsWith(".iso") || name.endsWith(".xbe")) {
                iconView.setImageResource(iconMap.get("iso"));
            } else if (name.endsWith(".cue") || name.endsWith(".bin") || name.endsWith(".cso")) {
                iconView.setImageResource(iconMap.get("cue"));
            } else if (name.endsWith(".deb")) {
                iconView.setImageResource(iconMap.get("deb"));
            } else if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh") || 
                       name.endsWith(".py") || name.endsWith(".pl") || name.endsWith(".rb")) {
                iconView.setImageResource(iconMap.get("script"));
            } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") ||
                       name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".aac") ||
                       name.endsWith(".wma")) {
                iconView.setImageResource(iconMap.get("audio"));
            } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".conf") || 
                       name.endsWith(".ini") || name.endsWith(".md")) {
                iconView.setImageResource(iconMap.get("text"));
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || 
                       name.endsWith(".gif") || name.endsWith(".bmp") || name.endsWith(".webp")) {
                iconView.setImageResource(iconMap.get("image"));
            } else if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
                       name.endsWith(".mov") || name.endsWith(".wmv") || name.endsWith(".flv")) {
                iconView.setImageResource(iconMap.get("video"));
            } else if (name.endsWith(".pdf")) {
                iconView.setImageResource(iconMap.get("pdf"));
            } else if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || 
                       name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".tar.xz") || 
                       name.endsWith(".tar.gz")) {
                iconView.setImageResource(iconMap.get("archive"));
            } else if (name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".yml") ||
                       name.endsWith(".yaml") || name.endsWith(".properties")) {
                iconView.setImageResource(iconMap.get("config"));
            } else if (name.endsWith(".doc") || name.endsWith(".docx") || name.endsWith(".odt")) {
                iconView.setImageResource(iconMap.get("document"));
            } else if (name.endsWith(".xls") || name.endsWith(".xlsx") || name.endsWith(".ods")) {
                iconView.setImageResource(iconMap.get("spreadsheet"));
            } else if (name.endsWith(".ppt") || name.endsWith(".pptx") || name.endsWith(".odp")) {
                iconView.setImageResource(iconMap.get("presentation"));
            } else if (name.endsWith(".db") || name.endsWith(".sqlite") || name.endsWith(".mdb")) {
                iconView.setImageResource(iconMap.get("database"));
            } else if (name.endsWith(".ttf") || name.endsWith(".otf") || name.endsWith(".woff")) {
                iconView.setImageResource(iconMap.get("font"));
            } else if (name.endsWith(".html") || name.endsWith(".htm") || name.endsWith(".css") ||
                       name.endsWith(".js") || name.endsWith(".php")) {
                iconView.setImageResource(iconMap.get("web"));
            } else if (name.endsWith(".java") || name.endsWith(".c") || name.endsWith(".cpp") ||
                       name.endsWith(".h") || name.endsWith(".cs") || name.endsWith(".swift")) {
                iconView.setImageResource(iconMap.get("programming"));
            } else {
                iconView.setImageResource(iconMap.get("default"));
            }
        }

        private boolean setExeIcon(ImageView iconView, File exeFile) {
            File iconFile = new File(ICON_DIR, exeFile.getName().replace(".exe", ".png"));
            if (iconFile.exists()) {
                try {
                    Bitmap bitmap = BitmapFactory.decodeFile(iconFile.getAbsolutePath());
                    if (bitmap != null) {
                        iconView.setImageBitmap(bitmap);
                        return true;
                    }
                } catch (Exception e) {
                    Log.e("IconLoad", "Failed to load icon: " + e.getMessage());
                }
            } else {
                if (!generatingIcons.contains(exeFile.getAbsolutePath())) {
                    generatingIcons.add(exeFile.getAbsolutePath());
                    generateExeIcon(exeFile, exeFile.getName().replace(".exe", ""), false);
                }
            }
            return false;
        }

        private String formatFileSize(long size) {
            if (size <= 0) return "0 B";
            final String[] units = new String[]{"B", "KB", "MB", "GB", "TB"};
            int digitGroups = (int) (Math.log10(size) / Math.log10(1024));
            return String.format("%.1f %s", size / Math.pow(1024, digitGroups), units[digitGroups]);
        }
    }

    static class FileHolder {
        ImageView icon;
        TextView name;
        TextView size;
    }

    /** Generate EXE icon and optionally refresh view **/
    private void generateExeIcon(File exeFile, String baseName, boolean refreshAfter) {
        new Thread(() -> {
            try {
                File iconFile = new File(ICON_DIR, baseName + ".png");
                if (!iconFile.exists()) {
                    Log.d("IconGen", "Generating icon for: " + exeFile.getName());
                    
                    String cmd = "wrestool -x --type=14 \"" + exeFile.getAbsolutePath() + "\" 2>/dev/null | magick ico:- \"" + iconFile.getAbsolutePath() + "\" 2>/dev/null";
                    
                    boolean success = runCommandWithProcessBuilder(cmd);
                    
                    if (success) {
                        Log.d("IconGen", "Successfully generated icon: " + iconFile.getName());
                        
                        File[] numberedIcons = ICON_DIR.listFiles((dir, name) -> 
                            name.startsWith(baseName + "-") && name.endsWith(".png"));
                        
                        if (numberedIcons != null) {
                            for (File numberedIcon : numberedIcons) {
                                numberedIcon.delete();
                            }
                        }
                    }
                    
                    if (refreshAfter && fileAdapter != null) {
                        ((android.app.Activity) mContext).runOnUiThread(() -> {
                            fileAdapter.notifyDataSetChanged();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("IconGen", "Failed to generate icon: " + e.getMessage());
            } finally {
                generatingIcons.remove(exeFile.getAbsolutePath());
            }
        }).start();
    }

    /** Generate icons for all EXE files in current directory **/
    private void generateExeIconsForDirectory(File[] files) {
        new Thread(() -> {
            for (File file : files) {
                if (file.isFile() && file.getName().toLowerCase().endsWith(".exe")) {
                    String baseName = file.getName().replace(".exe", "");
                    File iconFile = new File(ICON_DIR, baseName + ".png");
                    
                    if (!iconFile.exists() && !generatingIcons.contains(file.getAbsolutePath())) {
                        generatingIcons.add(file.getAbsolutePath());
                        generateExeIcon(file, baseName, true);
                    }
                }
            }
        }).start();
    }

    /** Handle click: open folder or handle file **/
    private void handleItemClick(File file) {
        if (file.isDirectory()) {
            loadDirectory(file);
            return;
        }

        String name = file.getName().toLowerCase();

        if (name.endsWith(".exe")) {
            showExeOptions(file);
        } else if (name.endsWith(".iso") || name.endsWith(".xiso") || name.endsWith(".xbe")) {
            showXdvdfsOptions(file);
        } else if (name.endsWith(".cue") || name.endsWith(".bin") || name.endsWith(".cso")) {
            showPs1Options(file);
        } else if (name.endsWith(".7z") || name.endsWith(".rar") || name.endsWith(".zip") || 
                   name.endsWith(".tar") || name.endsWith(".gz")) {
            showArchiveOptions(file);
        } else if (name.endsWith(".tar.xz") || name.endsWith(".tar.gz")) {
            showSystemArchiveOptions(file);
        } else if (name.endsWith(".deb")) {
            showDebOptions(file);
        } else if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh") ||
                   name.endsWith(".py") || name.endsWith(".pl") || name.endsWith(".rb")) {
            showScriptOptions(file);
        } else if (name.endsWith(".mp3") || name.endsWith(".ogg") || name.endsWith(".wav") ||
                   name.endsWith(".flac") || name.endsWith(".m4a") || name.endsWith(".aac")) {
            showAudioOptions(file);
        } else {
            Toast.makeText(mContext, 
                mContext.getString(R.string.selected_files) + ": " + file.getName(), 
                Toast.LENGTH_SHORT).show();
        }
    }

    /** Show options for EXE files **/
    private void showExeOptions(File exeFile) {
        String[] options = {
            mContext.getString(R.string.run_in_terminal),
            mContext.getString(R.string.create_shortcut)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("EXE File: " + exeFile.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        runExeInSession(exeFile);
                    } else if (which == 1) {
                        createExeShortcut(exeFile);
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Show options for Xbox files (ISO/XISO/XBE) with xdvdfs commands **/
    private void showXdvdfsOptions(File xboxFile) {
        String fileType = xboxFile.getName().toLowerCase().endsWith(".iso") ? "ISO" : 
                         xboxFile.getName().toLowerCase().endsWith(".xiso") ? "XISO" : "ISO";
        
        String[] options = {
            "📋 " + mContext.getString(R.string.list_files),
            "🌳 " + mContext.getString(R.string.tree_view), 
            "📊 " + mContext.getString(R.string.image_info),
            "📦 " + mContext.getString(R.string.unpack_image),
            "🎮 " + mContext.getString(R.string.open_with_xbox),
            "🎮 " + mContext.getString(R.string.open_with_ps1)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle(fileType + " File: " + xboxFile.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Files with 7z
                            runCommandAndShowInAlert("7z l \"" + xboxFile.getAbsolutePath() + "\"", 
                                "File List (7z): " + xboxFile.getName());
                            break;
                        case 1: // Tree View
                            runXdvdfsCommandInAlert(xboxFile, "tree", "File Tree: " + xboxFile.getName());
                            break;
                        case 2: // Image Info
                            runXdvdfsCommandInAlert(xboxFile, "info", "Image Info: " + xboxFile.getName());
                            break;
                        case 3: // Unpack Image
                            String outputDir = xboxFile.getName().replaceFirst("\\.[^.]+$", "") + "_extracted";
                            File outputFolder = new File(currentDir, outputDir);
                            String command = "xdvdfs unpack \"" + xboxFile.getAbsolutePath() + "\" \"" + outputFolder.getAbsolutePath() + "\"";
                            runCommandInSession(command, "Unpacking to " + outputDir);
                            break;
                        case 4: // Open with Xbox
                            runCommandInSession("xbox-z \"" + xboxFile.getAbsolutePath() + "\"", 
                                mContext.getString(R.string.opening_with_xbox));
                            break;
                        case 5: // Open with PS1
                            runCommandInSession("ps1-z \"" + xboxFile.getAbsolutePath() + "\"", 
                                mContext.getString(R.string.open_with_ps1));
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Show options for DEB files */
    private void showDebOptions(File debFile) {
        String[] options = {
            mContext.getString(R.string.install_with_downgrade),
            mContext.getString(R.string.cancel)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle(String.format(mContext.getString(R.string.deb_package), debFile.getName()))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        showInstallMethodDialog(debFile);
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Show installation method dialog */
    private void showInstallMethodDialog(File debFile) {
        String[] options = {
            mContext.getString(R.string.install_in_proot),
            mContext.getString(R.string.install_normally)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle(mContext.getString(R.string.installation_method))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        installDebInProot(debFile);
                    } else if (which == 1) {
                        installDebNormally(debFile);
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Install DEB in PRoot environment */
    private void installDebInProot(File debFile) {
        new Thread(() -> {
            try {
                String distro = findFirstProotDistro();
                if (distro == null) {
                    ((android.app.Activity) mContext).runOnUiThread(() -> {
                        Toast.makeText(mContext, 
                            mContext.getString(R.string.no_proot_distro), 
                            Toast.LENGTH_LONG).show();
                    });
                    return;
                }

                String command = "proot-distro login " + distro + " --root -- bash -c \"dpkg -i --force-downgrades '" + 
                               debFile.getAbsolutePath() + "' || apt-get install -f\"";
                
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    runCommandInSession(command, 
                        String.format(mContext.getString(R.string.installing_in_proot), distro));
                });

            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, 
                        "Failed to install in PRoot: " + e.getMessage(), 
                        Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** Find first available PRoot distro */
    private String findFirstProotDistro() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", "proot-distro list | grep -E '^[a-zA-Z]' | head -1 | awk '{print $1}'");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String distro = reader.readLine();
            process.waitFor();
            
            return (distro != null && !distro.trim().isEmpty()) ? distro.trim() : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** Install DEB normally with downgrade option */
    private void installDebNormally(File debFile) {
     //   String command = "dpkg -i --force-downgrades \"" + debFile.getAbsolutePath() + "\" || apt-get install -f";
        String command = "apt install -y --allow-downgrades \"" + debFile.getAbsolutePath() + "\" || apt install -f -y";
        runCommandInSession(command, String.format(mContext.getString(R.string.installing_package), debFile.getName()));
    }

    /** Show options for script files */
    private void showScriptOptions(File scriptFile) {
        String[] options = {
            mContext.getString(R.string.execute_in_terminal),
            mContext.getString(R.string.change_permissions),
            mContext.getString(R.string.cancel)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle(String.format(mContext.getString(R.string.script_file), scriptFile.getName()))
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // Execute in terminal
                            executeScriptInTerminal(scriptFile);
                            break;
                        case 1: // Change permissions
                            changePermissionsRecursive(scriptFile);
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Execute script in terminal */
    private void executeScriptInTerminal(File scriptFile) {
        String command = "bash \"" + scriptFile.getAbsolutePath() + "\"";
        runCommandInSession(command, String.format(mContext.getString(R.string.executing_script), scriptFile.getName()));
    }

    /** Change permissions recursively to executable */
    private void changePermissionsRecursive(File file) {
        new Thread(() -> {
            try {
                String command;
                if (file.isDirectory()) {
                    command = "chmod +x -R \"" + file.getAbsolutePath() + "\"";
                } else {
                    command = "chmod +x \"" + file.getAbsolutePath() + "\"";
                }
                
                boolean success = runCommandWithProcessBuilder(command);
                
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    if (success) {
                        Toast.makeText(mContext, 
                            mContext.getString(R.string.permissions_changed), 
                            Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(mContext, 
                            mContext.getString(R.string.failed_change_permissions), 
                            Toast.LENGTH_SHORT).show();
                    }
                });
                
            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, 
                        "Error: " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /** Change permissions for all selected files */
    private void changePermissionsForSelectedFiles() {
        new Thread(() -> {
            try {
                for (File file : selectedFiles) {
                    String command;
                    if (file.isDirectory()) {
                        command = "chmod +x -R \"" + file.getAbsolutePath() + "\"";
                    } else {
                        command = "chmod +x \"" + file.getAbsolutePath() + "\"";
                    }
                    runCommandWithProcessBuilder(command);
                }
                
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, 
                        String.format(mContext.getString(R.string.permissions_changed_count), selectedFiles.size()), 
                        Toast.LENGTH_SHORT).show();
                    
                    // Clear selection after operation
                    selectedFiles.clear();
                    isSelectionMode = false;
                    selectButton.setText(mContext.getString(R.string.select_mode_off));
                    selectButton.setBackgroundColor(0xFF444444);
                    fileAdapter.notifyDataSetChanged();
                });
                
            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, 
                        mContext.getString(R.string.failed_change_permissions) + ": " + e.getMessage(), 
                        Toast.LENGTH_SHORT).show();
                });
            }
        }).start();
    }

    /** Show options for audio files */
    private void showAudioOptions(File audioFile) {
        String[] options = {
            mContext.getString(R.string.open_with_mpv),
            mContext.getString(R.string.cancel)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle(String.format(mContext.getString(R.string.audio_file), audioFile.getName()))
                .setItems(options, (d, which) -> {
                    if (which == 0) {
                        openWithMpv(audioFile);
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Open audio file with MPV */
    private void openWithMpv(File audioFile) {
        String command = "mpv \"" + audioFile.getAbsolutePath() + "\"";
        runCommandInSession(command, String.format(mContext.getString(R.string.playing_audio), audioFile.getName()));
    }

    /** Run command and show output in alert */
    private void runCommandAndShowInAlert(String cmd, String title) {
        new Thread(() -> {
            try {
                String output = runCommandAndCaptureOutput(cmd);
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    showTextDialog(title, output);
                });
            } catch (Exception e) {
                ((android.app.Activity) mContext).runOnUiThread(() -> {
                    Toast.makeText(mContext, "Failed to run command: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }).start();
    }

    /** Show options for PS1 files **/
    private void showPs1Options(File ps1File) {
        String[] options = {
            "📋 " + mContext.getString(R.string.list_files),
            "📦 " + mContext.getString(R.string.extract_to_folder),
            "🎮 " + mContext.getString(R.string.open_with_ps1)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("PS1 File: " + ps1File.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Files
                            runCommandAndShowInAlert("7z l \"" + ps1File.getAbsolutePath() + "\"", 
                                "File List: " + ps1File.getName());
                            break;
                        case 1: // Extract to New Folder
                            extractArchiveToNewFolder(ps1File);
                            break;
                        case 2: // Open with PS1
                            runCommandInSession("ps1-z \"" + ps1File.getAbsolutePath() + "\"", 
                                mContext.getString(R.string.open_with_ps1));
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Show options for archive files **/
    private void showArchiveOptions(File archiveFile) {
        String[] options = {
            "📋 " + mContext.getString(R.string.list_files),
            "📦 " + mContext.getString(R.string.extract_to_folder)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("Archive: " + archiveFile.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Files
                            runCommandAndShowInAlert("7z l \"" + archiveFile.getAbsolutePath() + "\"", 
                                "File List: " + archiveFile.getName());
                            break;
                        case 1: // Extract to New Folder
                            extractArchiveToNewFolder(archiveFile);
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Extract archive to new folder named after the archive **/
    private void extractArchiveToNewFolder(File archiveFile) {
        String archiveName = archiveFile.getName();
        String folderName = archiveName;
        
        if (folderName.toLowerCase().endsWith(".tar.gz")) {
            folderName = folderName.substring(0, folderName.length() - 7);
        } else if (folderName.toLowerCase().endsWith(".tar.xz")) {
            folderName = folderName.substring(0, folderName.length() - 7);
        } else {
            int lastDot = folderName.lastIndexOf('.');
            if (lastDot > 0) {
                folderName = folderName.substring(0, lastDot);
            }
        }
        
        File outputDir = new File(currentDir, folderName);
        String command = "7z x \"" + archiveFile.getAbsolutePath() + "\" -o\"" + outputDir.getAbsolutePath() + "\" -y";
        runCommandInSession(command, mContext.getString(R.string.extracting_to) + " " + folderName);
        Toast.makeText(mContext, 
            mContext.getString(R.string.extracting_to) + ": " + folderName, 
            Toast.LENGTH_LONG).show();
    }

    /** Show options for system archive files **/
    private void showSystemArchiveOptions(File archiveFile) {
        String[] options = {
            "📋 " + mContext.getString(R.string.list_files),
            "📦 " + mContext.getString(R.string.extract_to_folder),
            mContext.getString(R.string.restore_to_xodos)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("System Archive: " + archiveFile.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Files
                            runCommandAndShowInAlert("7z l \"" + archiveFile.getAbsolutePath() + "\"", 
                                "File List: " + archiveFile.getName());
                            break;
                        case 1: // Extract to New Folder
                            extractArchiveToNewFolder(archiveFile);
                            break;
                        case 2: // Restore to XoDos
                            restoreSystemArchive(archiveFile);
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Restore system archive **/
    private void restoreSystemArchive(File archiveFile) {
        String command = "tar -xvf \"" + archiveFile.getAbsolutePath() + "\" -C /data/data/com.termux/files --preserve-permissions";
        runCommandInSession(command, mContext.getString(R.string.restoring_system_archive));
        Toast.makeText(mContext, 
            mContext.getString(R.string.restoring_system_archive), 
            Toast.LENGTH_LONG).show();
    }

    /** Run EXE directly in terminal session with X11 server **/
    private void runExeInSession(File exeFile) {
        if (!(mContext instanceof TermuxActivity)) {
            Toast.makeText(mContext, mContext.getString(R.string.can_only_run_from_termux), Toast.LENGTH_LONG).show();
            return;
        }

        String wineType = getWineType();
        String wineCommand = getWineCommand(wineType);
        
        String command = "termux-x11 :0 >/dev/null 2>&1 & sleep 3 && " + 
                        wineCommand + " \"" + exeFile.getAbsolutePath() + "\"";
        
        runCommandInSession(command, mContext.getString(R.string.running_with_x11) + " " + exeFile.getName());
    }

    /** Create shortcut for EXE file **/
    private void createExeShortcut(File exeFile) {
        try {
            String baseName = exeFile.getName().replace(".exe", "");
            File lnkFile = new File(DESKTOP_DIR, baseName + ".lnk");
            
            if (!lnkFile.exists()) {
                lnkFile.createNewFile();
            }
            
            generateExeIcon(exeFile, baseName, false);
            createDesktopFile(exeFile, baseName);
            
            Toast.makeText(mContext, 
                mContext.getString(R.string.shortcut_created) + ": " + baseName, 
                Toast.LENGTH_SHORT).show();
            
        } catch (Exception e) {
            Toast.makeText(mContext, 
                mContext.getString(R.string.failed_to_create_shortcut) + ": " + e.getMessage(), 
                Toast.LENGTH_LONG).show();
            Log.e("ShortcutCreate", "Error: ", e);
        }
    }

    /** Create desktop file **/
    private void createDesktopFile(File exeFile, String baseName) {
        try {
            String wineType = getWineType();
            String wineCommand = getWineCommand(wineType);
            File desktopFile = new File(DESKTOP_DIR, baseName + ".desktop");
            File iconFile = new File(ICON_DIR, baseName + ".png");

            try (PrintWriter out = new PrintWriter(desktopFile)) {
                out.println("[Desktop Entry]");
                out.println("Type=Application");
                out.println("Name=" + baseName);
                out.println("Exec=" + wineCommand + " \"" + exeFile.getAbsolutePath() + "\"");
                out.println("Icon=" + iconFile.getAbsolutePath());
                out.println("Terminal=false");
                out.println("Categories=Game;");
            }
        } catch (Exception e) {
            Log.e("DesktopCreate", "Failed to create desktop file: " + e.getMessage());
        }
    }

    /** Run command in active terminal session **/
    private void runCommandInSession(String cmd, String message) {
        if (!(mContext instanceof TermuxActivity)) {
            runCommandWithProcessBuilder(cmd);
            return;
        }

        TermuxActivity termuxActivity = (TermuxActivity) mContext;

        try {
            String fullCommand = cmd + "\n";
            
            Log.d("FileManagerCommand", "Executing in session: " + fullCommand);

            if (termuxActivity.getTermuxTerminalSessionClient() != null) {
                TerminalSession session = termuxActivity.getTermuxTerminalSessionClient().getCurrentStoredSessionOrLast();
                if (session != null && session.isRunning()) {
                    session.write(fullCommand);
                    showTerminalView(termuxActivity);
                    Toast.makeText(mContext, message, Toast.LENGTH_SHORT).show();
                    closeAndShowTerminal();
                    return;
                }
            }

            runCommandWithProcessBuilder(cmd);

        } catch (Exception e) {
            Log.e("FileManagerCommand", "Failed to execute in session: " + e.getMessage());
            runCommandWithProcessBuilder(cmd);
        }
    }

    /** Fallback method using ProcessBuilder **/
    private boolean runCommandWithProcessBuilder(String cmd) {
        try {
            Log.d("FileManagerCommand", "Fallback executing: " + cmd);
            
            ProcessBuilder processBuilder = new ProcessBuilder("sh", "-c", cmd);
            
            Map<String, String> env = processBuilder.environment();
            env.put("PATH", "/data/data/com.termux/files/usr/bin:/system/bin");
            env.put("LD_LIBRARY_PATH", "/data/data/com.termux/files/usr/lib");
            env.put("PREFIX", "/data/data/com.termux/files/usr");
            env.put("HOME", "/data/data/com.termux/files/home");
            env.put("TMPDIR", "/data/data/com.termux/files/usr/tmp");
            
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                Log.d("CommandOutput", line);
            }
            
            int exitCode = process.waitFor();
            Log.d("CommandExecution", "Exit code: " + exitCode);
            return exitCode == 0;
            
        } catch (Exception e) {
            Log.e("CommandError", "Fallback also failed: " + e.getMessage());
            return false;
        }
    }

    /** Get wine type from shared preferences **/
    private String getWineType() {
        try {
            android.content.SharedPreferences prefs = mContext.getSharedPreferences("com.termux_preferences", Context.MODE_PRIVATE);
            return prefs.getString("WINE_TYPE", "bionic");
        } catch (Exception e) {
            return "bionic";
        }
    }

    /** Get the appropriate Wine command based on type **/
    private String getWineCommand(String wineType) {
        if ("glibc".equals(wineType)) {
            return "/data/data/com.termux/files/usr/glibc/opt/scripts/xodos_wine";
        } else {
            return "/data/data/com.termux/files/usr/bin/xbio";
        }
    }

    /** Show terminal view **/
    private void showTerminalView(TermuxActivity termuxActivity) {
        if (termuxActivity.getMainContentView() != null) {
            termuxActivity.getMainContentView().setTerminalViewSwitchSlider(true);
        }
    }

    /** Close the file manager and show terminal **/
    private void closeAndShowTerminal() {
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
        if (mContext instanceof TermuxActivity) {
            showTerminalView((TermuxActivity) mContext);
        }
    }

    /** Go to Termux home **/
    private void goHome() {
        loadDirectory(new File(TERMUX_HOME));
    }
}