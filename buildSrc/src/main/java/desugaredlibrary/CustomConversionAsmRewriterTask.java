// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;

public class CustomConversionAsmRewriterTask extends DefaultTask {

  private final WorkerExecutor workerExecutor;

  private File rawJar;
  private File outputDirectory;

  @Inject
  public CustomConversionAsmRewriterTask(WorkerExecutor workerExecutor) {
    this.workerExecutor = workerExecutor;
  }

  @InputFile
  public File getRawJar() {
    return rawJar;
  }

  public void setRawJar(File rawJar) {
    this.rawJar = rawJar;
  }

  @OutputDirectory
  public File getOutputDirectory() {
    return outputDirectory;
  }

  public void setOutputDirectory(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  @TaskAction
  void exec() {
    workerExecutor.submit(
        Run.class,
        config -> {
          config.setIsolationMode(IsolationMode.NONE);
          config.params(rawJar, outputDirectory);
        });
  }

  public static class Run implements Runnable {

    private final File rawJar;
    private final File outputDirectory;

    @Inject
    public Run(File rawJar, File outputDirectory) {
      this.rawJar = rawJar;
      this.outputDirectory = outputDirectory;
    }

    @Override
    public void run() {
      try {
        CustomConversionAsmRewriter.generateJars(rawJar.toPath(), outputDirectory.toPath());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
