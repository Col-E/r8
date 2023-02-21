// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package dx;

import com.google.common.base.Optional;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.JavaForkOptions;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkQueue;
import org.gradle.workers.WorkerExecutor;
import utils.Utils;

public class DxTask extends DefaultTask {

  private final WorkerExecutor workerExecutor;

  private FileCollection source;
  private File destination;
  private Optional<File> dxExecutable = Optional.absent(); // Worker API cannot handle null.
  private boolean debug;

  @Inject
  public DxTask(WorkerExecutor workerExecutor) {
    this.workerExecutor = workerExecutor;
  }

  @InputFiles
  public FileCollection getSource() {
    return source;
  }

  public void setSource(FileCollection source) {
    this.source = source;
  }

  @OutputDirectory
  public File getDestination() {
    return destination;
  }

  public void setDestination(File destination) {
    this.destination = destination;
  }

  @InputFile
  @org.gradle.api.tasks.Optional
  public File getDxExecutable() {
    return dxExecutable.orNull();
  }

  public void setDxExecutable(File dxExecutable) {
    this.dxExecutable = Optional.fromNullable(dxExecutable);
  }

  @Input
  public boolean isDebug() {
    return debug;
  }

  public void setDebug(boolean debug) {
    this.debug = debug;
  }

  @TaskAction
  void exec() {
    WorkQueue workQueue =
        workerExecutor.processIsolation(
            workerSpec -> {
              JavaForkOptions forkOptions = workerSpec.getForkOptions();
              if (!dxExecutable.isPresent()) {
                setDxExecutable(
                    forkOptions.getWorkingDir().toPath().resolve(Utils.dxExecutable()).toFile());
              }
            });
    workQueue.submit(
        RunDx.class,
        parameters -> {
          parameters.getSources().set(source.getFiles());
          parameters.getDestination().set(destination);
          parameters.getDxExecutable().set(dxExecutable.get());
          parameters.getDebug().set(debug);
        });
  }

  public interface RunDxParameters extends WorkParameters {

    SetProperty<File> getSources();

    RegularFileProperty getDestination();

    RegularFileProperty getDxExecutable();

    Property<Boolean> getDebug();
  }

  public abstract static class RunDx implements WorkAction<RunDxParameters> {

    @Override
    public void execute() {
      RunDxParameters parameters = getParameters();
      try {
        List<String> command = new ArrayList<>();
        command.add(parameters.getDxExecutable().getAsFile().get().getCanonicalPath());
        command.add("--dex");
        command.add("--output");
        command.add(parameters.getDestination().getAsFile().get().getCanonicalPath());
        if (parameters.getDebug().get()) {
          command.add("--debug");
        }
        for (File source : parameters.getSources().get()) {
          command.add(source.getCanonicalPath());
        }

        Process dx = new ProcessBuilder(command).inheritIO().start();
        int exitCode = dx.waitFor();
        if (exitCode != 0) {
          throw new RuntimeException("dx failed with code " + exitCode);
        }
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      } catch (InterruptedException e) {
        throw new RuntimeException(e);
      }
    }
  }
}
