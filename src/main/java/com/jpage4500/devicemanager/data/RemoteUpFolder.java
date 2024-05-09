package com.jpage4500.devicemanager.data;

import se.vidstige.jadb.RemoteFile;

public class RemoteUpFolder extends RemoteFile {
    public RemoteUpFolder() {
        super("..");
    }

    @Override
    public String getName() {
        return getPath();
    }

    @Override
    public int getSize() {
        return 0;
    }

    @Override
    public int getLastModified() {
        return 0;
    }

    @Override
    public boolean isDirectory() {
        return true;
    }

    @Override
    public boolean isSymbolicLink() {
        return false;
    }

    @Override
    public String getPath() {
        return super.getPath();
    }
}
