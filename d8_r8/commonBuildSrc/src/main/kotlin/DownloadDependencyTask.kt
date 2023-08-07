// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.io.RandomAccessFile
import java.lang.Thread.sleep
import java.nio.channels.FileChannel
import java.nio.channels.FileLock
import java.nio.channels.OverlappingFileLockException
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
  fun setDependency(sha1File: File, outputDir: File, dependencyType: DependencyType) {
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
    if (!shouldExecute(outputDir, tarGzFile, sha1File)) {
      return
    }
    // Create a lock to ensure sequential a single downloader per third party dependency.
    val lockFile = sha1File.parentFile.resolve(sha1File.name + ".download_deps_lock")
    if (!lockFile.exists()) {
      lockFile.createNewFile()
    }
    getWorkerExecutor()!!
      .noIsolation()
      .submit(RunDownload::class.java) {
        type.set(dependencyType)
        this.sha1File.set(sha1File)
        this.outputDir.set(outputDir)
        this.tarGzFile.set(tarGzFile)
        this.lockFile.set(lockFile)
      }
  }

  interface RunDownloadParameters : WorkParameters {
    val type : Property<DependencyType>
    val sha1File : RegularFileProperty
    val outputDir : RegularFileProperty
    val tarGzFile : RegularFileProperty
    val lockFile : RegularFileProperty
  }

  abstract class RunDownload : WorkAction<RunDownloadParameters> {
    override fun execute() {
      var lock : FileLock? = null
      try {
        val sha1File = parameters.sha1File.asFile.get()
        val outputDir = parameters.outputDir.asFile.get()
        val tarGzFile = parameters.tarGzFile.asFile.get()
        if (!shouldExecute(outputDir, tarGzFile, sha1File)) {
          return;
        }
        val lockFile = parameters.lockFile.asFile.get()
        val channel: FileChannel = RandomAccessFile(lockFile, "rw").getChannel()
        // Block until we have the lock.
        var couldTakeLock = false
        while (!couldTakeLock) {
          try {
            lock = channel.lock()
            couldTakeLock = true;
          } catch (ignored: OverlappingFileLockException) {
            sleep(50);
          }
        }
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
      } catch (e: Exception) {
        throw RuntimeException(e)
      } finally {
        lock?.release()
      }
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun downloadFromGoogleStorage(parameters: RunDownloadParameters, sha1File: File) {
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
    private fun downloadFromX20(parameters: RunDownloadParameters, sha1File: File) {
      if (OperatingSystem.current().isWindows) {
        throw RuntimeException("Downloading from x20 unsupported on windows")
      }
      runProcess(parameters,
        ProcessBuilder()
          .command("bash", "-c", "tools/download_from_x20.py $sha1File"))
    }

    @Throws(IOException::class, InterruptedException::class)
    private fun runProcess(parameters: RunDownloadParameters, builder: ProcessBuilder) {
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
        && outputDir.list().isNotEmpty()
        && tarGzFile.exists()
        && sha1File.lastModified() <= tarGzFile.lastModified()) {
        return false
      }
      return true
    }
  }
}