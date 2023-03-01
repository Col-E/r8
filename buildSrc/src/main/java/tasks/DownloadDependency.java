// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package tasks;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import javax.inject.Inject;
import org.gradle.api.DefaultTask;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.TaskAction;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.workers.WorkAction;
import org.gradle.workers.WorkParameters;
import org.gradle.workers.WorkerExecutor;

public class DownloadDependency extends DefaultTask {

  public enum Type {
    GOOGLE_STORAGE,
    X20
  }

  private final WorkerExecutor workerExecutor;

  private Type type;
  private File outputDir;
  private File tarGzFile;
  private File sha1File;

  @Inject
  public DownloadDependency(WorkerExecutor workerExecutor) {
    this.workerExecutor = workerExecutor;
  }

  public void setType(Type type) {
    this.type = type;
  }

  public void setDependency(String dependency) {
    outputDir = new File(dependency);
    tarGzFile = new File(dependency + ".tar.gz");
    sha1File = new File(dependency + ".tar.gz.sha1");
  }

  @InputFiles
  public Collection<File> getInputFiles() {
    return Arrays.asList(sha1File, tarGzFile);
  }

  @OutputDirectory
  public File getOutputDir() {
    return outputDir;
  }

  public File getSha1File() {
    return sha1File;
  }

  public File getTarGzFile() {
    return tarGzFile;
  }

  @TaskAction
  public void execute() throws IOException, InterruptedException {
    if (!sha1File.exists()) {
      throw new RuntimeException("Missing sha1 file: " + sha1File);
    }

    // First run will write the tar.gz file, causing the second run to still be out-of-date.
    // Check if the modification time of the tar is newer than the sha in which case we are done.
    // Also, check the contents of the out directory because gradle appears to create it for us...
    if (outputDir.exists()
        && outputDir.isDirectory()
        && outputDir.list().length > 0
        && tarGzFile.exists()
        && sha1File.lastModified() <= tarGzFile.lastModified()) {
      return;
    }
    if (outputDir.exists() && outputDir.isDirectory()) {
      outputDir.delete();
    }
    workerExecutor
        .noIsolation()
        .submit(
            RunDownload.class,
            parameters -> {
              parameters.getType().set(type);
              parameters.getSha1File().set(sha1File);
            });
  }

  public interface RunDownloadParameters extends WorkParameters {
    Property<Type> getType();

    RegularFileProperty getSha1File();
  }

  public abstract static class RunDownload implements WorkAction<RunDownloadParameters> {

    @Override
    public void execute() {
      try {
        RunDownloadParameters parameters = getParameters();
        Type type = parameters.getType().get();
        File sha1File = parameters.getSha1File().getAsFile().get();
        if (type == Type.GOOGLE_STORAGE) {
          downloadFromGoogleStorage(sha1File);
        } else if (type == Type.X20) {
          downloadFromX20(sha1File);
        } else {
          throw new RuntimeException("Unexpected or missing dependency type: " + type);
        }
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    private void downloadFromGoogleStorage(File sha1File) throws IOException, InterruptedException {
      List<String> args = Arrays.asList("-n", "-b", "r8-deps", "-s", "-u", sha1File.toString());
      if (OperatingSystem.current().isWindows()) {
        List<String> command = new ArrayList<>();
        command.add("download_from_google_storage.bat");
        command.addAll(args);
        runProcess(new ProcessBuilder().command(command));
      } else {
        runProcess(
            new ProcessBuilder()
                .command("bash", "-c", "download_from_google_storage " + String.join(" ", args)));
      }
    }

    private void downloadFromX20(File sha1File) throws IOException, InterruptedException {
      if (OperatingSystem.current().isWindows()) {
        throw new RuntimeException("Downloading from x20 unsupported on windows");
      }
      runProcess(
          new ProcessBuilder()
              .command("bash", "-c", "tools/download_from_x20.py " + sha1File.toString()));
    }

    private static void runProcess(ProcessBuilder builder)
        throws IOException, InterruptedException {
      String command = String.join(" ", builder.command());
      Process p = builder.start();
      int exit = p.waitFor();
      if (exit != 0) {
        throw new IOException(
            "Process failed for "
                + command
                + "\n"
                + new BufferedReader(
                new InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n")));
      }
    }
  }
}
