// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package smali;

import static java.util.stream.Collectors.toList;

import com.android.tools.smali.smali.Smali;
import com.android.tools.smali.smali.SmaliOptions;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.FileTree;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

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
    workerExecutor
        .noIsolation()
        .submit(
            RunSmali.class,
            parameters -> {
              parameters.getSources().set(source.getFiles());
              parameters.getDestination().set(destination);
            });
  }

  public interface RunSmaliParameters extends WorkParameters {

    SetProperty<File> getSources();

    RegularFileProperty getDestination();
  }

  public abstract static class RunSmali implements WorkAction<RunSmaliParameters> {

    @Override
    public void execute() {
      try {
        RunSmaliParameters parameters = getParameters();
        List<String> fileNames =
            parameters.getSources().get().stream().map(File::toString).collect(toList());
        SmaliOptions options = new SmaliOptions();
        options.outputDexFile = parameters.getDestination().getAsFile().get().getCanonicalPath();
        Smali.assemble(options, fileNames);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }
  }
}
