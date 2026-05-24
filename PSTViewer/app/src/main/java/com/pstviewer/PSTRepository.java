package com.pstviewer;

import com.pff.PSTFile;
import com.pff.PSTFolder;
import com.pff.PSTMessage;

import java.util.ArrayList;
import java.util.List;

/**
 * Singleton that holds the currently-open PSTFile so Activities can share it
 * without passing large objects through Intents.
 */
public class PSTRepository {

    private static PSTRepository instance;

    private PSTFile pstFile;
    private String pstPath;

    // Cached list of top-level folders (set after opening)
    private List<PSTFolder> rootFolders = new ArrayList<>();

    private PSTRepository() {}

    public static synchronized PSTRepository getInstance() {
        if (instance == null) {
            instance = new PSTRepository();
        }
        return instance;
    }

    public void setPstFile(PSTFile file, String path) {
        // Close previous file if open
        if (this.pstFile != null) {
            try { this.pstFile.close(); } catch (Exception ignored) {}
        }
        this.pstFile = file;
        this.pstPath = path;
        this.rootFolders.clear();
    }

    public PSTFile getPstFile() { return pstFile; }
    public String getPstPath()  { return pstPath; }

    public boolean isOpen() { return pstFile != null; }

    public void close() {
        if (pstFile != null) {
            try { pstFile.close(); } catch (Exception ignored) {}
            pstFile = null;
            pstPath = null;
            rootFolders.clear();
        }
    }

    // ---------------------------------------------------------------------------
    // Helper: collect all sub-folders recursively for display in a flat list
    // ---------------------------------------------------------------------------
    public static List<FolderItem> flattenFolders(PSTFolder parent, int depth) {
        List<FolderItem> result = new ArrayList<>();
        try {
            if (parent.getContentCount() > 0) {
                result.add(new FolderItem(parent, depth));
            }
            if (parent.hasSubfolders()) {
                for (PSTFolder sub : parent.getSubFolders()) {
                    result.addAll(flattenFolders(sub, depth + 1));
                }
            }
        } catch (Exception e) {
            // skip unreadable folders
        }
        return result;
    }

    // ---------------------------------------------------------------------------
    // Helper: load all messages from a folder into a list
    // ---------------------------------------------------------------------------
    public static List<PSTMessage> loadMessages(PSTFolder folder) {
        List<PSTMessage> messages = new ArrayList<>();
        try {
            if (folder.getContentCount() > 0) {
                folder.moveChildCursorTo(0);
                PSTMessage message;
                while ((message = (PSTMessage) folder.getNextChild()) != null) {
                    messages.add(message);
                }
            }
        } catch (Exception e) {
            // partial results are fine
        }
        return messages;
    }

    // ---------------------------------------------------------------------------
    // Simple wrapper so we can pass folder + depth together
    // ---------------------------------------------------------------------------
    public static class FolderItem {
        public final PSTFolder folder;
        public final int depth;

        public FolderItem(PSTFolder folder, int depth) {
            this.folder = folder;
            this.depth  = depth;
        }

        public String getDisplayName() {
            String name = folder.getDisplayName();
            return (name == null || name.isEmpty()) ? "(unnamed)" : name;
        }

        public int getCount() {
            return folder.getContentCount();
        }
    }
}
