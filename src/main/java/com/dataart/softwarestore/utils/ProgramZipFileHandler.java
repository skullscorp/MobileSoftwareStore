package com.dataart.softwarestore.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.*;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

@Component
public class ProgramZipFileHandler {

    private static final Logger LOG = Logger.getLogger(ProgramZipFileHandler.class);

    public File transferFileToDir(CommonsMultipartFile sourceFile, File targetDir) throws IOException {
        if (!targetDir.exists()) targetDir.mkdir();
        File targetFile = new File(targetDir, sourceFile.getOriginalFilename());
        LOG.debug("Transferring program file to: " + targetFile.getAbsolutePath());
        sourceFile.transferTo(targetFile);
        return targetFile;
    }

    public List<File> extractZipFile(File file, File extractPath) throws IOException {
        ZipFile zipFile = new ZipFile(file);
        List<File> extractedEntries = new LinkedList<>();

        try {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryDestination = new File(extractPath, entry.getName());
                extractedEntries.add(entryDestination);

                if (entry.isDirectory()) {
                    entryDestination.mkdirs();
                } else {
                    entryDestination.getParentFile().mkdirs();
                    InputStream in = zipFile.getInputStream(entry);
                    OutputStream out = new FileOutputStream(entryDestination);
                    IOUtils.copy(in, out);
                    IOUtils.closeQuietly(in);
                    out.close();
                }
            }
        } finally {
            zipFile.close();
        }
        return extractedEntries;
    }

    public void removeFiles(File... files) {
        for (File file : files) {
            if (file.exists()) {
                LOG.debug("Removing file or dir: " + file.getAbsolutePath());
                try {
                    FileUtils.forceDelete(file);
                } catch (IOException e) {
                    LOG.error("Unable to remove file or dir: " + file.getAbsolutePath() + ", Error msg: " + e.getMessage());
                }
            }
        }
    }

}
