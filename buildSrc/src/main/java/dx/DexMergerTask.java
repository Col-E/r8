// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package dx;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;
import utils.Utils;

public class DexMergerTask extends DefaultTask {

  private final WorkerExecutor workerExecutor;

  private FileCollection source;
  private File destination;

  @Inject
  public DexMergerTask(WorkerExecutor workerExecutor) {
    this.workerExecutor = workerExecutor;
  }

  @InputFiles
  public FileCollection getSource() {
    return source;
  }

  public void setSource(FileCollection source) {
    this.source = source;
  }

  @OutputFile
  public File getDestination() {
    return destination;
  }

  public void setDestination(File destination) {
    this.destination = destination;
  }

  @TaskAction
  void exec() {
    workerExecutor
        .noIsolation()
        .submit(
            RunDexMerger.class,
            parameters -> {
              parameters.getSources().set(source.getFiles());
              parameters.getDestination().set(destination);
            });
  }

  public interface RunDexMergerParameters extends WorkParameters {

    SetProperty<File> getSources();

    RegularFileProperty getDestination();

    RegularFileProperty getDexMergerExecutable();
  }

  public abstract static class RunDexMerger implements WorkAction<RunDexMergerParameters> {

    @Override
    public void execute() {
      try {
        RunDexMergerParameters parameters = getParameters();
        List<String> command = new ArrayList<>();
        command.add(Utils.dexMergerExecutable().toString());
        command.add(parameters.getDestination().getAsFile().get().getCanonicalPath());
        for (File source : parameters.getSources().get()) {
          command.add(source.getCanonicalPath());
        }
        Process dexMerger = new ProcessBuilder(command).inheritIO().start();
        int exitCode = dexMerger.waitFor();
        if (exitCode != 0) {
          throw new RuntimeException("Dex merger failed with code " + exitCode);
        }
      } catch (IOException e) {
        throw new java.io.UncheckedIOException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
