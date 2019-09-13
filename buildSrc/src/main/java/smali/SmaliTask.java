// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package smali;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileTree;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.IsolationMode;
import org.gradle.workers.WorkerExecutor;
import org.jf.smali.Smali;
import org.jf.smali.SmaliOptions;

public class SmaliTask extends DefaultTask {

  private final WorkerExecutor workerExecutor;

  private FileTree source;
  private File destination;

  @Inject
  public SmaliTask(WorkerExecutor workerExecutor) {
    this.workerExecutor = workerExecutor;
  }

  @InputFiles
  public FileTree getSource() {
    return source;
  }

  public void setSource(FileTree source) {
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
    workerExecutor.submit(RunSmali.class, config -> {
      config.setIsolationMode(IsolationMode.NONE);
      config.params(source.getFiles(), destination);
    });
  }

  public static class RunSmali implements Runnable {
    private final Set<File> sources;
    private final File destination;

    @Inject
    public RunSmali(Set<File> sources, File destination) {
      this.sources = sources;
      this.destination = destination;
    }

    @Override
    public void run() {
      try {
        List<String> fileNames = sources.stream().map(File::toString).collect(toList());
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = destination.getCanonicalPath();
        Smali.assemble(options, fileNames);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
