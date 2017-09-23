/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * /
 */

package utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * This class responsible for creating a archive of the created exporting directory
 */
class ArchiveGeneratorUtil {
    private static final Log log = LogFactory.getLog(ArchiveGeneratorUtil.class);

    /**
     * Archive the directory specified by source directory
     *
     * @param sourceDirectory Source directory path
     */
    static void archiveDirectory(String sourceDirectory) {

        File directoryToZip = new File(sourceDirectory);
        List<File> fileList = new ArrayList<>();
        getAllFiles(directoryToZip, fileList);
        writeArchiveFile(directoryToZip, fileList);
    }

    /**
     * Retrieve all the files included in the source directory to be archived
     *
     * @param sourceDirectory Source directory
     * @param fileList        List of files
     */
    private static void getAllFiles(File sourceDirectory, List<File> fileList) {
        File[] files = sourceDirectory.listFiles();
        if (files != null) {
            for (File file : files) {
                fileList.add(file);
                if (file.isDirectory()) {
                    getAllFiles(file, fileList);
                }
            }
        }
    }

    /**
     * Generate archive file
     *
     * @param directoryToZip Location of the directory to archive
     * @param fileList       List of files to be included in the archive
     */
    private static void writeArchiveFile(File directoryToZip, List<File> fileList) {

        FileOutputStream fileOutputStream = null;
        ZipOutputStream zipOutputStream = null;

        try {
            fileOutputStream = new FileOutputStream(directoryToZip.getPath() +
                    ImportExportConstants.ZIP_EXTENSION);
            zipOutputStream = new ZipOutputStream(fileOutputStream);
            for (File file : fileList) {
                if (!file.isDirectory()) {
                    addToArchive(directoryToZip, file, zipOutputStream);
                }
            }
            FileUtils.deleteDirectory(new File(directoryToZip.getPath()));
        } catch (IOException e) {
            String errorMessage = "I/O error while adding files to archive";
            log.error(errorMessage);
        } finally {
            IOUtils.closeQuietly(zipOutputStream);
            IOUtils.closeQuietly(fileOutputStream);
        }
    }

    /**
     * Add files of the directory to be archive
     *
     * @param directoryToZip  Location of the directory to be archive
     * @param file            File to be included in the archive
     * @param zipOutputStream Output stream
     */
    private static void addToArchive(File directoryToZip, File file, ZipOutputStream zipOutputStream) {

        FileInputStream fileInputStream = null;
        try {
            fileInputStream = new FileInputStream(file);

            // Get relative path from archive directory to the specific file
            String zipFilePath = file.getCanonicalPath()
                    .substring(directoryToZip.getCanonicalPath().length() + 1,
                            file.getCanonicalPath().length());
            if (File.separatorChar != ImportExportConstants.ZIP_FILE_SEPARATOR)
                zipFilePath = zipFilePath.replace(File.separatorChar,
                        ImportExportConstants.ZIP_FILE_SEPARATOR);
            ZipEntry zipEntry = new ZipEntry(zipFilePath);
            zipOutputStream.putNextEntry(zipEntry);
            IOUtils.copy(fileInputStream, zipOutputStream);
            zipOutputStream.closeEntry();
        } catch (IOException e) {
            String errorMessage = "I/O error while writing files to archive";
            log.error(errorMessage);
        } finally {
            IOUtils.closeQuietly(fileInputStream);
        }
    }

}
