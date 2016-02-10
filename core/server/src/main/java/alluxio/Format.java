/*
 * Licensed to the University of California, Berkeley under one or more contributor license
 * agreements. See the NOTICE file distributed with this work for additional information regarding
 * copyright ownership. The ASF licenses this file to You under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License. You may obtain a
 * copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package alluxio;

import alluxio.master.AlluxioMaster;
import alluxio.underfs.UnderFileSystem;
import alluxio.util.UnderFileSystemUtils;
import alluxio.util.io.PathUtils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Format Alluxio file system.
 */
@ThreadSafe
public final class Format {
  private static final Logger LOG = LoggerFactory.getLogger(Constants.LOGGER_TYPE);
  private static final String USAGE = String.format("java -cp %s alluxio.Format <MASTER/WORKER>",
      Version.ALLUXIO_JAR);

  private static boolean formatFolder(String name, String folder, Configuration configuration)
      throws IOException {
    UnderFileSystem ufs = UnderFileSystem.get(folder, configuration);
    LOG.info("Formatting {}:{}", name, folder);
    if (ufs.exists(folder)) {
      for (String file : ufs.list(folder)) {
        if (!ufs.delete(PathUtils.concatPath(folder, file), true)) {
          LOG.info("Failed to remove {}:{}", name, file);
          return false;
        }
      }
    } else if (!ufs.mkdirs(folder, true)) {
      LOG.info("Failed to create {}:{}", name, folder);
      return false;
    }
    return true;
  }

  /**
   * Formats the Alluxio file system via {@code java -cp %s alluxio.Format <MASTER/WORKER>}.
   *
   * @param args either {@code MASTER} or {@code WORKER}
   * @throws IOException if a non-Alluxio related exception occurs
   */
  public static void main(String[] args) throws IOException {
    if (args.length != 1) {
      LOG.info(USAGE);
      System.exit(-1);
    }

    Configuration configuration = new Configuration();

    if ("MASTER".equals(args[0].toUpperCase())) {

      String masterJournal =
          configuration.get(Constants.MASTER_JOURNAL_FOLDER);
      if (!formatFolder("JOURNAL_FOLDER", masterJournal, configuration)) {
        System.exit(-1);
      }

      for (String masterServiceName : AlluxioMaster.getServiceNames()) {
        if (!formatFolder(masterServiceName + "_JOURNAL_FOLDER", PathUtils.concatPath(masterJournal,
            masterServiceName), configuration)) {
          System.exit(-1);
        }
      }

      // A journal folder is thought to be formatted only when a file with the specific name is
      // present under the folder.
      UnderFileSystemUtils.touch(
          PathUtils.concatPath(masterJournal, Constants.FORMAT_FILE_PREFIX
              + System.currentTimeMillis()), configuration);
    } else if ("WORKER".equals(args[0].toUpperCase())) {
      String workerDataFolder = configuration.get(Constants.WORKER_DATA_FOLDER);
      int storageLevels = configuration.getInt(Constants.WORKER_TIERED_STORE_LEVELS);
      for (int level = 0; level < storageLevels; level++) {
        String tierLevelDirPath =
            String.format(Constants.WORKER_TIERED_STORE_LEVEL_DIRS_PATH_FORMAT, level);
        String[] dirPaths = configuration.get(tierLevelDirPath).split(",");
        String name = "TIER_" + level + "_DIR_PATH";
        for (String dirPath : dirPaths) {
          String dirWorkerDataFolder = PathUtils.concatPath(dirPath.trim(), workerDataFolder);
          UnderFileSystem ufs = UnderFileSystem.get(dirWorkerDataFolder, configuration);
          if (ufs.exists(dirWorkerDataFolder)) {
            if (!formatFolder(name, dirWorkerDataFolder, configuration)) {
              System.exit(-1);
            }
          }
        }
      }
    } else {
      LOG.info(USAGE);
      System.exit(-1);
    }
  }

  private Format() {}  // Prevent instantiation.
}