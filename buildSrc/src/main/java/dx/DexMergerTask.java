// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package dx;

import com.google.common.base.Optional;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.Action;
import org.gradle.api.DefaultTask;
import org.gradle.api.UncheckedIOException;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecSpec;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerConfiguration;
import org.gradle.workers.WorkerExecutor;
import utils.Utils;

public class DexMergerTask extends DefaultTask {

  private final WorkerExecutor workerExecutor;

  private FileCollection source;
  private File destination;
  private Optional<File> dexMergerExecutable = Optional.absent(); // Worker API cannot handle null.

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

  @InputFile
  @org.gradle.api.tasks.Optional
  public File getDexMergerExecutable() {
    return dexMergerExecutable.orNull();
  }

  public void setDexMergerExecutable(File dexMergerExecutable) {
    this.dexMergerExecutable = Optional.fromNullable(dexMergerExecutable);
  }

  @TaskAction
  void exec() {
    workerExecutor.submit(RunDexMerger.class, config -> {
      config.setIsolationMode(IsolationMode.NONE);
      config.params(source.getFiles(), destination, dexMergerExecutable);
    });
  }

  public static class RunDexMerger implements Runnable {
    private final Set<File> sources;
    private final File destination;
    private final Optional<File> dexMergerExecutable;

    @Inject
    public RunDexMerger(Set<File> sources, File destination, Optional<File> dexMergerExecutable) {
      this.sources = sources;
      this.destination = destination;
      this.dexMergerExecutable = dexMergerExecutable;
    }

    @Override
    public void run() {
      try {
        List<String> command = new ArrayList<>();
        command.add(dexMergerExecutable.or(Utils::dexMergerExecutable).getCanonicalPath());
        command.add(destination.getCanonicalPath());
        for (File source : sources) {
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
