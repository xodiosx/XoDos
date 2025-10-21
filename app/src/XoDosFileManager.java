package com.termux.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;
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

    // Navigation history
    private List<File> navigationHistory = new ArrayList<>();
    private int currentHistoryIndex = -1;

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
        
        // Add initial directory to history
        navigationHistory.add(currentDir);
        currentHistoryIndex = 0;
    }

    private void initializeAndroidIcons() {
        // Use standard Android icons for file types
        iconMap.put("folder", R.drawable.ifm);
        iconMap.put("parent", android.R.drawable.ic_menu_revert);
        
        // Standard Android file type icons
        iconMap.put("exe", android.R.drawable.ic_media_play);
        iconMap.put("iso", R.drawable.idsc);
        iconMap.put("cue", R.drawable.idsc);
        iconMap.put("bin", R.drawable.idsc);
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
        iconMap.put("xbox", R.drawable.ixbox); // Xbox icon
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

    /** Add directory to navigation history */
    private void addToHistory(File dir) {
        // Remove any forward history
        if (currentHistoryIndex < navigationHistory.size() - 1) {
            navigationHistory = navigationHistory.subList(0, currentHistoryIndex + 1);
        }
        
        // Add new directory if it's different from current
        if (navigationHistory.isEmpty() || 
            !navigationHistory.get(navigationHistory.size() - 1).getAbsolutePath().equals(dir.getAbsolutePath())) {
            navigationHistory.add(dir);
            currentHistoryIndex = navigationHistory.size() - 1;
        }
    }

    /** Navigate back in history */
    private void navigateBack() {
        if (currentHistoryIndex > 0) {
            currentHistoryIndex--;
            File prevDir = navigationHistory.get(currentHistoryIndex);
            loadDirectory(prevDir);
        }
    }

    /** Navigate forward in history */
    private void navigateForward() {
        if (currentHistoryIndex < navigationHistory.size() - 1) {
            currentHistoryIndex++;
            File nextDir = navigationHistory.get(currentHistoryIndex);
            loadDirectory(nextDir);
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
        backBtn.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        backParams.weight = 1;
        backBtn.setLayoutParams(backParams);
        backBtn.setOnClickListener(v -> navigateBack());

        // Forward button
        Button forwardBtn = new Button(mContext);
        forwardBtn.setText(mContext.getString(R.string.forward));
        forwardBtn.setTextColor(0xFFFFFFFF);
        forwardBtn.setTextSize(12);
        forwardBtn.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams forwardParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        forwardParams.weight = 1;
        forwardBtn.setLayoutParams(forwardParams);
        forwardBtn.setOnClickListener(v -> navigateForward());

        // Home button
        Button homeBtn = new Button(mContext);
        homeBtn.setText(mContext.getString(R.string.home));
        homeBtn.setTextColor(0xFFFFFFFF);
        homeBtn.setTextSize(12);
        homeBtn.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams homeParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        homeParams.weight = 1;
        homeBtn.setLayoutParams(homeParams);
        homeBtn.setOnClickListener(v -> goHome());

        // Paste button - smaller
        pasteButton = new Button(mContext);
        pasteButton.setText(mContext.getString(R.string.paste));
        pasteButton.setTextColor(0xFFFFFFFF);
        pasteButton.setTextSize(10);
        pasteButton.setPadding(4, 2, 4, 2);
        LinearLayout.LayoutParams pasteParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        pasteParams.weight = 0.5f; // Smaller weight for smaller button
        pasteButton.setLayoutParams(pasteParams);
        pasteButton.setEnabled(copiedFile != null);
        pasteButton.setOnClickListener(v -> pasteFile());

        // Help button
        Button helpBtn = new Button(mContext);
        helpBtn.setText(mContext.getString(R.string.help));
        helpBtn.setTextColor(0xFFFFFFFF);
        helpBtn.setTextSize(12);
        helpBtn.setPadding(8, 4, 8, 4);
        LinearLayout.LayoutParams helpParams = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        );
        helpParams.weight = 1;
        helpBtn.setLayoutParams(helpParams);
        helpBtn.setOnClickListener(v -> showHelpDialog());

        navLayout.addView(backBtn);
        navLayout.addView(forwardBtn);
        navLayout.addView(homeBtn);
        navLayout.addView(pasteButton);
        navLayout.addView(helpBtn);

        return navLayout;
    }

    /** Show help dialog with supported file types **/
    private void showHelpDialog() {
        String helpMessage = mContext.getString(R.string.help_message);

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
        addToHistory(dir);
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
            handleItemClick(selectedFile);
        });

        // Add long press listener for copy operation
        fileListView.setOnItemLongClickListener((parent, view, position, id) -> {
            File selectedFile = files[position];
            showCopyOptions(selectedFile);
            return true;
        });

        // Generate icons for EXE files in this directory
        generateExeIconsForDirectory(files);
    }

    /** Show copy options on long press */
    private void showCopyOptions(File file) {
        String[] options = {
            mContext.getString(R.string.copy), 
            mContext.getString(R.string.cancel)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle(file.getName())
                .setItems(options, (d, which) -> {
                    if (which == 0) { // Copy
                        copiedFile = file;
                        Toast.makeText(mContext, 
                            mContext.getString(R.string.copied) + ": " + file.getName(), 
                            Toast.LENGTH_SHORT).show();
                        refreshPasteButton();
                    }
                    // Cancel does nothing
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
                    
                    byte[] buffer = new byte[8192]; // Larger buffer for better performance
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
            row.setBackgroundColor(0xFF333333); // Dark background
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
            name.setTextColor(0xFFFFFFFF); // White text
            name.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ));
            textContainer.addView(name);

            // File size/type
            TextView size = new TextView(context);
            size.setId(android.R.id.text2);
            size.setTextSize(12);
            size.setTextColor(0xFFCCCCCC); // Light gray text
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
                // Try to load actual EXE icon, fallback to default EXE icon
                if (!setExeIcon(iconView, file)) {
                    iconView.setImageResource(iconMap.get("exe"));
                }
            } else if (name.endsWith(".iso") || name.endsWith(".xiso")) {
                iconView.setImageResource(iconMap.get("iso"));
            } else if (name.endsWith(".cue") || name.endsWith(".bin")) {
                iconView.setImageResource(iconMap.get("cue"));
            } else if (name.endsWith(".txt") || name.endsWith(".log") || name.endsWith(".conf") || 
                       name.endsWith(".ini")) {
                iconView.setImageResource(iconMap.get("text"));
            } else if (name.endsWith(".sh") || name.endsWith(".bash") || name.endsWith(".zsh")) {
                iconView.setImageResource(iconMap.get("script"));
            } else if (name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || 
                       name.endsWith(".gif") || name.endsWith(".bmp")) {
                iconView.setImageResource(iconMap.get("image"));
            } else if (name.endsWith(".mp3") || name.endsWith(".wav") || name.endsWith(".ogg") ||
                       name.endsWith(".flac")) {
                iconView.setImageResource(iconMap.get("audio"));
            } else if (name.endsWith(".mp4") || name.endsWith(".avi") || name.endsWith(".mkv") ||
                       name.endsWith(".mov")) {
                iconView.setImageResource(iconMap.get("video"));
            } else if (name.endsWith(".pdf")) {
                iconView.setImageResource(iconMap.get("pdf"));
            } else if (name.endsWith(".zip") || name.endsWith(".rar") || name.endsWith(".7z") || 
                       name.endsWith(".tar") || name.endsWith(".gz") || name.endsWith(".tar.xz") || name.endsWith(".tar.gz")) {
                iconView.setImageResource(iconMap.get("archive"));
            } else if (name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".yml") ||
                       name.endsWith(".yaml")) {
                iconView.setImageResource(iconMap.get("config"));
            } else {
                iconView.setImageResource(iconMap.get("default"));
            }
        }

        private boolean setExeIcon(ImageView iconView, File exeFile) {
            // Try to extract and load EXE icon
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
                // If icon doesn't exist and we're not already generating it, start generation
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
                    
                    // Simple icon extraction command - extracts directly to the ico folder
                    String cmd = "wrestool -x --type=14 \"" + exeFile.getAbsolutePath() + "\" 2>/dev/null | magick ico:- \"" + iconFile.getAbsolutePath() + "\" 2>/dev/null";
                    
                    // Run the command using ProcessBuilder (background task)
                    boolean success = runCommandWithProcessBuilder(cmd);
                    
                    if (success) {
                        Log.d("IconGen", "Successfully generated icon: " + iconFile.getName());
                        
                        // Check if we got multiple numbered files and clean them up
                        File[] numberedIcons = ICON_DIR.listFiles((dir, name) -> 
                            name.startsWith(baseName + "-") && name.endsWith(".png"));
                        
                        if (numberedIcons != null) {
                            for (File numberedIcon : numberedIcons) {
                                numberedIcon.delete();
                            }
                        }
                    }
                    
                    if (refreshAfter && fileAdapter != null) {
                        // Refresh the list view on UI thread
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
        } else if (name.endsWith(".iso") || name.endsWith(".xiso")) {
            showIsoOptions(file);
        } else if (name.endsWith(".cue") || name.endsWith(".bin")) {
            showPs1Options(file);
        } else if (name.endsWith(".7z") || name.endsWith(".rar") || name.endsWith(".zip") || 
                   name.endsWith(".tar") || name.endsWith(".gz")) {
            showArchiveOptions(file);
        } else if (name.endsWith(".tar.xz") || name.endsWith(".tar.gz")) {
            showSystemArchiveOptions(file);
        } else {
            Toast.makeText(mContext, 
                mContext.getString(R.string.selected) + ": " + file.getName(), 
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

    /** Run EXE directly in terminal session with X11 server **/
    private void runExeInSession(File exeFile) {
        if (!(mContext instanceof TermuxActivity)) {
            Toast.makeText(mContext, mContext.getString(R.string.can_only_run_from_termux), Toast.LENGTH_LONG).show();
            return;
        }

        String wineType = getWineType();
        String wineCommand = getWineCommand(wineType);
        
        // Start X11 server and then run the EXE
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
            
            // Generate icon for the EXE
            generateExeIcon(exeFile, baseName, false);
            
            // Create desktop file using XoDosShortcuts logic
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

    /** Enhanced ISO options with 7z support **/
    private void showIsoOptions(File isoFile) {
        String[] options = {
            mContext.getString(R.string.list_contents),
            mContext.getString(R.string.extract_to_new_folder),
            mContext.getString(R.string.open_with_xbox),
            mContext.getString(R.string.convert_to_xbox_iso)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("ISO File: " + isoFile.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Contents
                            listArchiveContents(isoFile);
                            break;
                        case 1: // Extract to New Folder
                            extractArchiveToNewFolder(isoFile);
                            break;
                        case 2: // Open with Xbox
                            runCommandInSession("xbox-z \"" + isoFile.getAbsolutePath() + "\"", 
                                mContext.getString(R.string.opening_with_xbox));
                            break;
                        case 3: // Convert to Xbox ISO
                            convertToXboxIso(isoFile);
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Show options for PS1 files **/
    private void showPs1Options(File ps1File) {
        String[] options = {
            mContext.getString(R.string.list_contents),
            mContext.getString(R.string.extract_to_new_folder),
            mContext.getString(R.string.open_with_ps1)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("PS1 File: " + ps1File.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Contents
                            listArchiveContents(ps1File);
                            break;
                        case 1: // Extract to New Folder
                            extractArchiveToNewFolder(ps1File);
                            break;
                        case 2: // Open with PS1
                            runCommandInSession("ps1-z \"" + ps1File.getAbsolutePath() + "\"", 
                                mContext.getString(R.string.opening_with_ps1));
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** Show options for archive files **/
    private void showArchiveOptions(File archiveFile) {
        String[] options = {
            mContext.getString(R.string.list_contents),
            mContext.getString(R.string.extract_to_new_folder)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("Archive: " + archiveFile.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Contents
                            listArchiveContents(archiveFile);
                            break;
                        case 1: // Extract to New Folder
                            extractArchiveToNewFolder(archiveFile);
                            break;
                    }
                })
                .setNegativeButton(mContext.getString(R.string.cancel), null)
                .show();
    }

    /** List archive contents using 7z **/
    private void listArchiveContents(File archiveFile) {
        String command = "7z l \"" + archiveFile.getAbsolutePath() + "\"";
        runCommandInSession(command, mContext.getString(R.string.listing_contents) + " " + archiveFile.getName());
    }

    /** Extract archive to new folder named after the archive **/
    private void extractArchiveToNewFolder(File archiveFile) {
        // Create folder name based on archive name without extension
        String archiveName = archiveFile.getName();
        String folderName = archiveName;
        
        // Remove common extensions
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

    /** Convert to Xbox ISO format **/
    private void convertToXboxIso(File isoFile) {
        String outputName = isoFile.getName().replaceFirst("\\.(x?iso)$", "_xbox.\\1");
        File outputFile = new File(isoFile.getParent(), outputName);
        
        String command = "extract-xiso -r \"" + isoFile.getAbsolutePath() + "\" -o \"" + outputFile.getAbsolutePath() + "\"";
        runCommandInSession(command, mContext.getString(R.string.converting_to_xbox_iso));
        Toast.makeText(mContext, 
            mContext.getString(R.string.converting_to_xbox_iso_format) + ": " + outputName, 
            Toast.LENGTH_LONG).show();
    }

    /** Show options for system archive files **/
    private void showSystemArchiveOptions(File archiveFile) {
        String[] options = {
            mContext.getString(R.string.list_contents),
            mContext.getString(R.string.extract_to_new_folder),
            mContext.getString(R.string.restore_to_xodos)
        };
        
        new AlertDialog.Builder(mContext)
                .setTitle("System Archive: " + archiveFile.getName())
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0: // List Contents
                            listArchiveContents(archiveFile);
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

    /** Go back one folder **/
    private void goBack() {
        File parent = currentDir.getParentFile();
        if (parent == null) return;

        if (parent.getAbsolutePath().startsWith(TERMUX_HOME) ||
            parent.getAbsolutePath().startsWith(TERMUX_DATA)) {
            loadDirectory(parent);
        } else {
            Toast.makeText(mContext, mContext.getString(R.string.access_denied), Toast.LENGTH_SHORT).show();
        }
    }

    /** Go to Termux home **/
    private void goHome() {
        loadDirectory(new File(TERMUX_HOME));
    }
}