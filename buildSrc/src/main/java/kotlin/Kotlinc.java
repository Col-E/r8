// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package kotlin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;
import utils.Utils;

/**
 * Gradle task to compile Kotlin source files.
 */
public class Kotlinc extends DefaultTask {

  enum KotlinTargetVersion {
    JAVA_6("1.6"),
    JAVA_8("1.8");

    private final String optionName;

    KotlinTargetVersion(String optionName) {
      this.optionName = optionName;
    }
  }

  @InputFiles
  private FileTree source;

  @OutputFile
  private File destination;

  private KotlinTargetVersion targetVersion;

  public FileTree getSource() {
    return source;
  }

  public void setSource(FileTree source) {
    this.source = source;
  }

  public File getDestination() {
    return destination;
  }

  public void setDestination(File destination) {
    this.destination = destination;
  }

  public KotlinTargetVersion getTargetVersion() {
    return targetVersion;
  }

  public void setTargetVersion(KotlinTargetVersion targetVersion) {
    this.targetVersion = targetVersion;
  }

  @TaskAction
  public void compile() {
    getProject().exec(new Action<ExecSpec>() {
      @Override
      public void execute(ExecSpec execSpec) {
        try {
          String kotlincExecName = Utils.toolsDir().equals("windows") ? "kotlinc.bat" : "kotlinc";
          Path kotlincExecPath = Paths
              .get("third_party", "kotlin", "kotlinc", "bin", kotlincExecName);
          execSpec.setExecutable(kotlincExecPath.toFile());
          execSpec.args("-include-runtime");
          execSpec.args("-nowarn");
          execSpec.args("-jvm-target", targetVersion.optionName);
          execSpec.args("-d", destination.getCanonicalPath());
          execSpec.args(source.getFiles());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }
    });
  }
}
