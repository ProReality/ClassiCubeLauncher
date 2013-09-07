package net.classicube.launcher;

import java.io.*;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.logging.Level;
import javax.swing.SwingWorker;

public class ClientUpdateTask extends SwingWorker<Boolean, Boolean> {

    private static final String ClientDownloadUrl = "http://www.classicube.net/static/client/client.jar",
            ClientHashUrl = "http://www.classicube.net/static/client/client.jar.md5";

    private ClientUpdateTask() {
    }
    private static ClientUpdateTask instance = new ClientUpdateTask();

    public static ClientUpdateTask getInstance() {
        return instance;
    }
    File targetPath, clientFile;

    @Override
    protected Boolean doInBackground() throws Exception {
        LogUtil.getLogger().log(Level.FINE, "ClientUpdateTask.doInBackground");
        targetPath = PathUtil.getLauncherDir();
        clientFile = PathUtil.getClientJar();

        final boolean needsUpdate = checkForClientUpdate();

        if (needsUpdate) {
            LogUtil.getLogger().log(Level.INFO, "Downloading.");
            getClientUpdate();
            LogUtil.getLogger().log(Level.INFO, "Update applied.");
        } else {
            LogUtil.getLogger().log(Level.INFO, "No update needed.");
        }

        return needsUpdate;
    }

    private boolean checkForClientUpdate()
            throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        boolean needsUpdate;
        if (!clientFile.exists()) {
            LogUtil.getLogger().log(Level.INFO, "No local copy, will download.");
            // if local file does not exist, always update/download
            needsUpdate = true;

        } else {
            LogUtil.getLogger().log(Level.INFO, "Checking for update.");
            // else check if remote hash is different from local hash
            final String remoteString = HttpUtil.downloadString(ClientHashUrl);
            final String remoteHash = remoteString.substring(0, 32);
            if (remoteHash == null) {
                LogUtil.getLogger().log(Level.INFO, "Error downloading remote hash, aborting.");
                needsUpdate = false; // remote server is down, dont try to update
            } else {
                final String localHashString = computeLocalHash(clientFile);
                needsUpdate = !localHashString.equalsIgnoreCase(remoteHash);
            }
        }
        return needsUpdate;
    }

    private void getClientUpdate()
            throws MalformedURLException, FileNotFoundException, IOException {
        // download (or re-download) the client
        final File clientTempFile = new File(targetPath, PathUtil.ClientTempJar);
        downloadClientJar(clientTempFile);
        PathUtil.replaceFile(clientTempFile, clientFile);
    }

    private String computeLocalHash(File clientJar)
            throws NoSuchAlgorithmException, FileNotFoundException, IOException {
        if (clientJar == null) {
            throw new NullPointerException("clientJar");
        }
        final MessageDigest digest = MessageDigest.getInstance("MD5");
        final byte[] buffer = new byte[8192];
        try (FileInputStream is = new FileInputStream(clientJar)) {
            final DigestInputStream dis = new DigestInputStream(is, digest);
            while (dis.read(buffer) != -1) {
                // DigestInputStream is doing its job, we just need to read through it.
            }
        }
        final byte[] localHashBytes = digest.digest();
        return new BigInteger(1, localHashBytes).toString(16);
    }

    private void downloadClientJar(File clientJar)
            throws MalformedURLException, FileNotFoundException, IOException {
        if (clientJar == null) {
            throw new NullPointerException("clientJar");
        }
        clientJar.delete();
        final URL website = new URL(ClientDownloadUrl);
        final ReadableByteChannel rbc = Channels.newChannel(website.openStream());
        try (FileOutputStream fos = new FileOutputStream(clientJar)) {
            fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
        }
    }
}