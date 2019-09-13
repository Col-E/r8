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
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.IsolationMode;
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
    workerExecutor.submit(RunDx.class, config -> {
      config.setIsolationMode(IsolationMode.NONE);
      config.params(source.getFiles(), destination, dxExecutable, debug);
    });
  }

  public static class RunDx implements Runnable {
    private final Set<File> sources;
    private final File destination;
    private final Optional<File> dxExecutable;
    private final boolean debug;

    @Inject
    public RunDx(Set<File> sources, File destination, Optional<File> dxExecutable, boolean debug) {
      this.sources = sources;
      this.destination = destination;
      this.dxExecutable = dxExecutable;
      this.debug = debug;
    }

    @Override
    public void run() {
      try {
        List<String> command = new ArrayList<>();
        command.add(dxExecutable.or(Utils::dxExecutable).getCanonicalPath());
        command.add("--dex");
        command.add("--output");
        command.add(destination.getCanonicalPath());
        if (debug) {
          command.add("--debug");
        }
        for (File source : sources) {
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
