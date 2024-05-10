package se.vidstige.jadb;

/**
 * Created by vidstige on 2014-03-20
 */
public class RemoteFile {
    private final String path;

    public RemoteFile(String path) { this.path = path; }

    public int getSize() { throw new UnsupportedOperationException(); }
    public int getLastModified() { throw new UnsupportedOperationException(); }
    public boolean isDirectory() { throw new UnsupportedOperationException(); }
    public boolean isSymbolicLink() { throw new UnsupportedOperationException(); }

    /**
     * @return full path to file
     */
    public String getPath() { return path;}

    /**
     * @return file name
     */
    public String getName() {
        if (path == null) return null;
        final int lastDot = path.lastIndexOf('/');
        return lastDot == -1 || (lastDot == path.length()-1) ? path : path.substring(lastDot + 1);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        RemoteFile that = (RemoteFile) o;
        return path.equals(that.path);
    }

    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
