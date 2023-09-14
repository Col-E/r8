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
import org.gradle.api.file.FileCollection
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectories
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.os.OperatingSystem
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import org.gradle.workers.WorkerExecutor

abstract class DownloadAllDependenciesTask : DefaultTask() {

  private var _root: File? = null
  private var _thirdPartyDeps: List<ThirdPartyDependency>? = null;

  @InputFiles
  fun getInputFile(): FileCollection {
    val sha1Files = _thirdPartyDeps!!.map { _root!!.resolve(it.sha1File) }
    return project.files(*sha1Files.toTypedArray())
  }

  @OutputDirectories
  fun getOutputDir(): FileCollection {
    val outputDirs = _thirdPartyDeps!!.map { _root!!.resolve(it.path) }
    return project.files(*outputDirs.toTypedArray())
  }

  @Inject
  protected abstract fun getWorkerExecutor(): WorkerExecutor?

  fun setDependencies(root: File, thirdPartyDeps: List<ThirdPartyDependency>) {
    this._root = root
    this._thirdPartyDeps = thirdPartyDeps;
  }

  @TaskAction
  fun execute() {
    val noIsolation = getWorkerExecutor()!!.noIsolation()
    _thirdPartyDeps?.forEach {
      val root = _root!!
      val sha1File = root.resolve(it.sha1File)
      val tarGzFile = sha1File.resolveSibling(sha1File.name.replace(".sha1", ""))
      val outputDir = root.resolve(it.path)
      if (!sha1File.exists()) {
        throw RuntimeException("Missing sha1 file: $sha1File")
      }
      if (shouldExecute(outputDir, tarGzFile, sha1File)) {
        println("Downloading ${it}")
        noIsolation.submit(RunDownload::class.java) {
          type.set(it.type)
          this.sha1File.set(sha1File)
          this.outputDir.set(outputDir)
          this.tarGzFile.set(tarGzFile)
          this.root.set(root)
        }
      }
    }
  }

  interface RunDownloadParameters : WorkParameters {
    val type : Property<DependencyType>
    val sha1File : RegularFileProperty
    val outputDir : RegularFileProperty
    val tarGzFile : RegularFileProperty
    val root : RegularFileProperty
  }

  abstract class RunDownload : WorkAction<RunDownloadParameters> {
    override fun execute() {
      val sha1File = parameters.sha1File.asFile.get()
      val outputDir = parameters.outputDir.asFile.get()
      val tarGzFile = parameters.tarGzFile.asFile.get()
      if (!shouldExecute(outputDir, tarGzFile, sha1File)) {
        return;
      }
      if (outputDir.exists() && outputDir.isDirectory) {
        outputDir.delete()
      }
      when (parameters.type.get()) {
        DependencyType.GOOGLE_STORAGE -> {
          downloadFromGoogleStorage(parameters, sha1File)
        }
        DependencyType.X20 -> {
          downloadFromX20(parameters, sha1File)
        }
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromGoogleStorage(parameters: RunDownload2Parameters, sha1File: File) {
      val args = Arrays.asList("-n", "-b", "r8-deps", "-s", "-u", sha1File.toString())
      if (OperatingSystem.current().isWindows) {
        val command: MutableList<String> = ArrayList()
        command.add("download_from_google_storage.bat")
        command.addAll(args)
        runProcess(parameters, ProcessBuilder().command(command))
      } else {
        runProcess(
          parameters,
          ProcessBuilder()
            .command("bash",
                     "-c",
                     "download_from_google_storage " + java.lang.String.join(" ", args)))
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromX20(parameters: RunDownload2Parameters, sha1File: File) {
      if (OperatingSystem.current().isWindows) {
        throw RuntimeException("Downloading from x20 unsupported on windows")
      }
      runProcess(parameters,
        ProcessBuilder()
          .command("bash", "-c", "tools/download_from_x20.py $sha1File"))
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runProcess(parameters: RunDownload2Parameters, builder: ProcessBuilder) {
      builder.directory(parameters.root.asFile.get())
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

  companion object {
    fun shouldExecute(outputDir: File, tarGzFile: File, sha1File: File) : Boolean {
      // First run will write the tar.gz file, causing the second run to still be out-of-date.
      // Check if the modification time of the tar is newer than the sha in which case we are done.
      if (outputDir.exists()
        && outputDir.isDirectory
        && outputDir.list()!!.isNotEmpty()
        && tarGzFile.exists()
        && sha1File.lastModified() <= tarGzFile.lastModified()) {
        return false
      }
      return true
    }
  }
}