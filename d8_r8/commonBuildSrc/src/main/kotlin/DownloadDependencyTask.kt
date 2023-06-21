// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.stream.Collectors
import javax.inject.Inject
import org.gradle.api.DefaultTask
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.options.Option
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

abstract class DownloadDependencyTask : DefaultTask() {

  private var dependencyType: DependencyType = DependencyType.GOOGLE_STORAGE
  private var _outputDir: File? = null
  private var _tarGzFile: File? = null
  private var _sha1File: File? = null

  @OutputDirectory
  fun getOutputDir(): File? {
    return _outputDir
  }

  @Inject
  protected abstract fun getWorkerExecutor(): WorkerExecutor?

  @Option(
    option = "dependency",
    description = "Sets the dependency information for a cloud stored file")
  fun setDependency(
    dependencyName : String, sha1File: File, outputDir : File, dependencyType: DependencyType) {
    _outputDir = outputDir
    _sha1File = sha1File
    _tarGzFile = sha1File.resolveSibling(sha1File.name.replace(".sha1", ""))
    this.dependencyType = dependencyType
  }

  @TaskAction
  fun execute() {
    val sha1File = _sha1File!!
    val outputDir = _outputDir!!
    val tarGzFile = _tarGzFile!!
    if (!sha1File.exists()) {
      throw RuntimeException("Missing sha1 file: $sha1File")
    }
    // First run will write the tar.gz file, causing the second run to still be out-of-date.
    // Check if the modification time of the tar is newer than the sha in which case we are done.
    // Also, check the contents of the out directory because gradle appears to create it for us...
    if (outputDir.exists()
      && outputDir.isDirectory
      && outputDir.list().isNotEmpty() && tarGzFile.exists()
      && sha1File.lastModified() <= tarGzFile.lastModified()) {
      return
    }
    if (outputDir.exists() && outputDir.isDirectory) {
      outputDir.delete()
    }
    getWorkerExecutor()!!
      .noIsolation()
      .submit(RunDownload::class.java) {
        this.type.set(dependencyType)
        this.sha1File.set(sha1File)
      }
  }


  interface RunDownloadParameters : WorkParameters {
    val type : Property<DependencyType>
    val sha1File : RegularFileProperty
  }

  abstract class RunDownload : WorkAction<RunDownloadParameters> {
    override fun execute() {
      try {
        val parameters: RunDownloadParameters = parameters
        val type: DependencyType = parameters.type.get()
        val sha1File: File = parameters.sha1File.asFile.get()
        if (type == DependencyType.GOOGLE_STORAGE) {
          downloadFromGoogleStorage(sha1File)
        } else if (type == DependencyType.X20) {
          downloadFromX20(sha1File)
        } else {
          throw RuntimeException("Unexpected or missing dependency type: $type")
        }
      } catch (e: Exception) {
        throw RuntimeException(e)
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromGoogleStorage(sha1File: File) {
      val args = Arrays.asList("-n", "-b", "r8-deps", "-s", "-u", sha1File.toString())
      if (OperatingSystem.current().isWindows) {
        val command: MutableList<String> = ArrayList()
        command.add("download_from_google_storage.bat")
        command.addAll(args)
        runProcess(ProcessBuilder().command(command))
      } else {
        runProcess(
          ProcessBuilder()
            .command("bash",
                     "-c",
                     "download_from_google_storage " + java.lang.String.join(" ", args)))
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromX20(sha1File: File) {
      if (OperatingSystem.current().isWindows) {
        throw RuntimeException("Downloading from x20 unsupported on windows")
      }
      runProcess(
        ProcessBuilder()
          .command("bash", "-c", "tools/download_from_x20.py $sha1File"))
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runProcess(builder: ProcessBuilder) {
      val command = java.lang.String.join(" ", builder.command())
      val p = builder.start()
      val exit = p.waitFor()
      if (exit != 0) {
        throw IOException("Process failed for $command\n"
            + BufferedReader(
            InputStreamReader(p.errorStream, StandardCharsets.UTF_8))
            .lines()
            .collect(Collectors.joining("\n")))
      }
    }
  }
}