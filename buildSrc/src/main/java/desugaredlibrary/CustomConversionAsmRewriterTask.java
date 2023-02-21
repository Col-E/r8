// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package desugaredlibrary;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
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
    workerExecutor
        .noIsolation()
        .submit(
            Run.class,
            parameters -> {
              parameters.getRawJar().set(rawJar);
              parameters.getOutputDirectory().set(outputDirectory);
            });
  }

  public interface RunParameters extends WorkParameters {
    RegularFileProperty getRawJar();

    RegularFileProperty getOutputDirectory();
  }

  public abstract static class Run implements WorkAction<RunParameters> {

    @Override
    public void execute() {
      try {
        RunParameters parameters = getParameters();
        CustomConversionAsmRewriter.generateJars(
            parameters.getRawJar().getAsFile().get().toPath(),
            parameters.getOutputDirectory().getAsFile().get().toPath());
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
