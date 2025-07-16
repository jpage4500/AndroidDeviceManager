package se.vidstige.jadb.managers;

import com.jpage4500.devicemanager.data.SplitManifest;
import com.jpage4500.devicemanager.utils.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import se.vidstige.jadb.JadbDevice;
import se.vidstige.jadb.JadbException;
import se.vidstige.jadb.RemoteFile;
import se.vidstige.jadb.Stream;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Java interface to package manager. Launches package manager through jadb
 */
public class PackageManager {
    private static final Logger log = LoggerFactory.getLogger(PackageManager.class);

    private final JadbDevice device;

    public PackageManager(JadbDevice device) {
        this.device = device;
    }

    public List<Package> getPackages() throws IOException, JadbException {
        try (BufferedReader input = new BufferedReader(new InputStreamReader(device.executeShell("pm", "list", "packages"), StandardCharsets.UTF_8))) {
            ArrayList<Package> result = new ArrayList<>();
            String line;
            while ((line = input.readLine()) != null) {
                final String prefix = "package:";
                if (line.startsWith(prefix)) {
                    result.add(new Package(line.substring(prefix.length())));
                }
            }
            return result;
        }
    }

    private String getErrorMessage(String operation, String target, String errorMessage) {
        return "Could not " + operation + " " + target + ": " + errorMessage;
    }

    private void verifyOperation(String operation, String target, String result) throws JadbException {
        if (!result.contains("Success")) throw new JadbException(getErrorMessage(operation, target, result));
    }

    private void remove(RemoteFile file) throws IOException, JadbException {
        InputStream s = device.executeShell("rm", "-f", file.getPath());
        Stream.readAll(s, StandardCharsets.UTF_8);
    }

    private void install(File apkFile, List<String> extraArguments) throws IOException, JadbException {
        String apkName = apkFile.getName();
        if (apkName.endsWith(".xapk") || apkName.endsWith(".apkm")) {
            installSplit(apkFile, extraArguments);
            return;
        }
        RemoteFile remote = new RemoteFile("/data/local/tmp/" + apkName);
        device.push(apkFile, remote);
        List<String> arguments = new ArrayList<>();
        arguments.add("install");
        arguments.addAll(extraArguments);
        arguments.add(remote.getPath());
        InputStream s = device.executeShell("pm", arguments.toArray(new String[0]));
        String result = Stream.readAll(s, StandardCharsets.UTF_8);
        remove(remote);
        verifyOperation("install", apkName, result);
    }

    /**
     * install split apk
     */
    private void installSplit(File splitApkFile, List<String> extraArguments) throws IOException, JadbException {
        Timer timer = new Timer();
        // 1) copy .xapk/.apkm file to temp location
        String origName = splitApkFile.getName();
        File tmpFile = new File(Utils.getTempFolder(), origName);
        log.trace("installSplit: copy file: {} -> {}", splitApkFile, tmpFile);
        try {
            Files.copy(splitApkFile.toPath(), tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            // 2) rename .xapk file to dm-install.zip
            File zipFile = new File(tmpFile.getParent(), "dm-install.zip");
            if (zipFile.exists()) zipFile.delete();
            tmpFile.renameTo(zipFile);

            File targetDir = new File(tmpFile.getParent(), "dm-install");

            // 3) extract zip to folder
            try (ZipFile zip = new ZipFile(zipFile)) {
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    File entryDestination = new File(targetDir, entry.getName());
                    if (entry.isDirectory()) {
                        log.trace("installSplit: create dir:{}", entryDestination);
                        entryDestination.mkdirs();
                    } else {
                        File parentFile = entryDestination.getParentFile();
                        if (!parentFile.exists()) {
                            log.trace("installSplit: create parent dir:{}", parentFile);
                            parentFile.mkdirs();
                        }
                        OutputStream out = new FileOutputStream(entryDestination);
                        log.trace("installSplit: extract: {}", entryDestination.getName());
                        zip.getInputStream(entry).transferTo(out);
                    }
                }
            }
            List<File> installFiles = new ArrayList<>();

            // find base apk file
            if (origName.endsWith(".apkm")) {
                File baseFile = new File(targetDir, "base.apk");
                if (!baseFile.exists()) {
                    log.error("installSplit: base.apk doesn't exist");
                    throw new JadbException("base.apk doesn't exist");
                }
                installFiles.add(baseFile);
            } else {
                File manifestFile = new File(targetDir, "manifest.json");
                if (!manifestFile.exists()) {
                    log.error("installSplit: manifest.json doesn't exist");
                    throw new JadbException("manifest.json doesn't exist");
                }
                log.trace("installSplit: reading manifest: {}", manifestFile);
                String json = Files.readString(manifestFile.toPath());
                SplitManifest splitManifest = GsonHelper.fromJson(json, SplitManifest.class);
                log.trace("installSplit: got manifest:{}", GsonHelper.toJson(splitManifest));
                if (splitManifest == null || splitManifest.splitApkList == null) {
                    log.error("installSplit: splitApkList not found");
                    throw new JadbException("splitApkList not found");
                }
                for (SplitManifest.SplitApk splitApk : splitManifest.splitApkList) {
                    if (TextUtils.equalsIgnoreCase(splitApk.id, "base")) {
                        File baseFile = new File(targetDir, splitApk.file);
                        installFiles.add(baseFile);
                        break;
                    }
                }
            }

            if (installFiles.isEmpty()) {
                log.error("installSplit: base not found");
                throw new JadbException("base not found");
            }

            // TODO: pass in architecture (arm64, x86, etc) to filter out apks for wrong architecture
            // TODO: pass in dpi (120, 160, 240, etc) to filter out apks for wrong dpi
            // TODO: pass in language to filter out apks for wrong language

            File[] files = targetDir.listFiles();
            if (files == null) {
                log.error("installSplit: targetDir is empty or doesn't exist: {}", targetDir);
                throw new JadbException("targetDir is empty or doesn't exist");
            }
            File langApk = findBestFile(files, ".en");
            if (langApk != null) installFiles.add(langApk);
            File osApk = findBestFile(files, "arm64_v8a", "armeabi_v7a");
            if (osApk != null) installFiles.add(osApk);
            File dpiApk = findBestFile(files, "xxhdpi", "xhdpi");
            if (dpiApk != null) installFiles.add(dpiApk);

            long totalSize = 0;
            for (File installFile : installFiles) totalSize += installFile.length();
            log.trace("installSplit: install-create: {}, files:{}", totalSize, installFiles.size());

            // pm install-create -S TOTAL_SIZE_OF_ALL_APKS
            InputStream s = device.executeShell("pm", "install-create", "-S", String.valueOf(totalSize));
            String result = Stream.readAll(s, StandardCharsets.UTF_8);
            verifyOperation("install-create", "", result);
            // Success: created install session [807594146]
            int stPos = result.indexOf('[');
            if (stPos == -1) throw new JadbException("invalid session " + result);
            int endPos = result.indexOf(']', stPos);
            if (endPos == -1) throw new JadbException("invalid session " + result);
            String session = result.substring(stPos + 1, endPos);
            log.trace("installSplit: session: {}", session);

            for (int i = 0; i < installFiles.size(); i++) {
                File apkFile = installFiles.get(i);
                long splitLen = apkFile.length();
                // push file to device
                log.trace("installSplit: installing:{}, len:{}", apkFile.getName(), splitLen);
                RemoteFile remote = new RemoteFile("/data/local/tmp/" + apkFile.getName());
                device.push(apkFile, remote);

                // pm install-write -S APK_SIZE SESSION_ID INDEX PATH
                s = device.executeShell("pm", "install-write", "-S", String.valueOf(splitLen), session, String.valueOf(i), remote.getPath());
                result = Stream.readAll(s, StandardCharsets.UTF_8);
                verifyOperation("install-write", remote.getName(), result);

                log.trace("installSplit: DONE:{}, {}, len:{}", timer, apkFile.getName(), splitLen);
                remove(remote);
            }

            // pm install-commit 4711
            log.trace("installSplit: COMMIT:{}, {}", timer, session);
            s = device.executeShell("pm", "install-commit", session);
            result = Stream.readAll(s, StandardCharsets.UTF_8);
            verifyOperation("install-commit", session, result);

            // 6) install .obb files (optional)

            // clean-up
            zipFile.delete();
            FileUtils.deleteFolder(targetDir);
        } catch (Exception e) {
            log.error("installSplit: ERROR:{}", e.getMessage());
            throw new JadbException("ERROR: " + e.getMessage());
        }
    }

    private File findBestFile(File[] files, String... searchForArr) {
        for (String searchFor : searchForArr) {
            for (File file : files) {
                String name = file.getName();
                if (!name.endsWith(".apk")) continue;
                else if (name.contains(searchFor)) return file;
            }
        }
        return null;
    }

    public void install(File apkFile) throws IOException, JadbException {
        install(apkFile, new ArrayList<>(0));
    }

    public void installWithOptions(File apkFile, List<? extends InstallOption> options) throws IOException, JadbException {
        List<String> optionsAsStr = new ArrayList<>(options.size());

        for (InstallOption installOption : options) {
            optionsAsStr.add(installOption.getStringRepresentation());
        }
        install(apkFile, optionsAsStr);
    }

    public void forceInstall(File apkFile) throws IOException, JadbException {
        installWithOptions(apkFile, Collections.singletonList(REINSTALL_KEEPING_DATA));
    }

    public void uninstall(Package name) throws IOException, JadbException {
        InputStream s = device.executeShell("pm", "uninstall", name.toString());
        String result = Stream.readAll(s, StandardCharsets.UTF_8);
        verifyOperation("uninstall", name.toString(), result);
    }

    public void launch(Package name) throws IOException, JadbException {
        InputStream s = device.executeShell("monkey", "-p", name.toString(), "-c", "android.intent.category.LAUNCHER", "1");
        s.close();
    }

    //<editor-fold desc="InstallOption">
    public static class InstallOption {
        private final StringBuilder stringBuilder = new StringBuilder();

        InstallOption(String... varargs) {
            String suffix = "";
            for (String str : varargs) {
                stringBuilder.append(suffix).append(str);
                suffix = " ";
            }
        }

        private String getStringRepresentation() {
            return stringBuilder.toString();
        }
    }

    public static final InstallOption WITH_FORWARD_LOCK = new InstallOption("-l");

    public static final InstallOption REINSTALL_KEEPING_DATA =
        new InstallOption("-r");

    public static final InstallOption ALLOW_TEST_APK =
        new InstallOption("-t");

    @SuppressWarnings("squid:S00100")
    public static InstallOption WITH_INSTALLER_PACKAGE_NAME(String name) {
        return new InstallOption("-t", name);
    }

    @SuppressWarnings("squid:S00100")
    public static InstallOption ON_SHARED_MASS_STORAGE(String name) {
        return new InstallOption("-s", name);
    }

    @SuppressWarnings("squid:S00100")
    public static InstallOption ON_INTERNAL_SYSTEM_MEMORY(String name) {
        return new InstallOption("-f", name);
    }

    public static final InstallOption ALLOW_VERSION_DOWNGRADE =
        new InstallOption("-d");

    /**
     * This option is supported only from Android 6.X+
     */
    public static final InstallOption GRANT_ALL_PERMISSIONS = new InstallOption("-g");

    //</editor-fold>
}
