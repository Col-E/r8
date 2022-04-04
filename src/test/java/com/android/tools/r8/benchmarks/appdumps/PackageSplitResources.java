// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.benchmarks.appdumps;

import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.ZipUtils;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class PackageSplitResources {

  private final List<Path> packageFiles;
  private final List<Path> otherFiles;

  public PackageSplitResources(List<Path> packageFiles, List<Path> otherFiles) {
    this.packageFiles = packageFiles;
    this.otherFiles = otherFiles;
  }

  public static PackageSplitResources create(
      TemporaryFolder temp, Path archive, List<String> packagePrefixes) throws IOException {
    Path unzipDir = temp.newFolder().toPath();
    ZipUtils.unzip(archive, unzipDir);
    List<Path> packageFiles = new ArrayList<>();
    List<Path> otherFiles = new ArrayList<>();
    Files.walk(unzipDir)
        .forEachOrdered(
            file -> {
              if (FileUtils.isClassFile(file)) {
                Path relative = unzipDir.relativize(file);
                if (isInPackagePrefixes(relative, packagePrefixes)) {
                  packageFiles.add(file);
                } else {
                  otherFiles.add(file);
                }
              }
            });

    return new PackageSplitResources(packageFiles, otherFiles);
  }

  private static boolean isInPackagePrefixes(Path file, List<String> programPackages) {
    String str = file.toString();
    if (File.separatorChar != '/') {
      str = str.replace(File.separatorChar, '/');
    }
    for (String packagePrefix : programPackages) {
      if (str.startsWith(packagePrefix)) {
        return true;
      }
    }
    return false;
  }

  public List<Path> getPackageFiles() {
    return packageFiles;
  }

  public List<Path> getOtherFiles() {
    return otherFiles;
  }
}
