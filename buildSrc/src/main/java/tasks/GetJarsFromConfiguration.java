// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package tasks;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.gradle.api.DefaultTask;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

/**
 * Extract all jars from the configuration. If an aar is in the configuration, classes.jar is
 * extracted. Locations of the jars are written to a generated file.
 */
public class GetJarsFromConfiguration extends DefaultTask {

  private Configuration configuration;

  @InputFiles
  public Configuration getInputFiles() {
    return configuration;
  }

  public void setConfiguration(Configuration configuration) {
    this.configuration = configuration;
  }

  @OutputDirectory
  public File getOutputDir() {
    return new File(getProject().getBuildDir(), "supportlibraries");
  }

  @OutputFile
  public File getGeneratedFile() {
    return new File(getProject().getBuildDir(), "generated/supportlibraries.txt");
  }

  @TaskAction
  public void extract() throws IOException {
    Files.createDirectories(getOutputDir().toPath());

    Set<File> configurationFiles = configuration.getFiles();
    List<String> jarPaths = new ArrayList<>(configurationFiles.size());
    for (File file : configurationFiles) {
      jarPaths.add(getSingleJar(file));
    }

    Path generatedPath = getGeneratedFile().toPath();
    Files.deleteIfExists(generatedPath);
    Files.createDirectories(generatedPath.getParent());
    Files.write(generatedPath, jarPaths);
  }

  private String getSingleJar(File jarOrAar) throws IOException {
    if (jarOrAar.getName().endsWith(".aar")) {
      FileTree aarEntries = getProject().zipTree(jarOrAar);

      for (File aarEntry : aarEntries) {
        if (aarEntry.getName().equals("classes.jar")) {
          try (InputStream is = new FileInputStream(aarEntry)) {
            String jarName = jarOrAar.getName().replaceAll("\\.aar$", ".jar");
            Path extractedPath = getOutputDir().toPath().resolve(jarName);
            Files.deleteIfExists(extractedPath);
            Files.copy(is, extractedPath);

            return extractedPath.toString();
          }
        }
      }
      throw new RuntimeException("Aar does not contain classes.jar: " + jarOrAar.toString());
    } else {
      return jarOrAar.toString();
    }
  }
}
