// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintStream
import java.net.URLEncoder
import java.nio.file.Path
import java.util.concurrent.TimeUnit
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType

// Utility to install tracking of test results in status files.
class TestingState {
  companion object {

    const val MODE_PROPERTY = "testing-state-mode"
    const val PATH_PROPERTY = "testing-state-path"

    const val PAST_FAILURE = "PAST_FAILURE"

    // Operating mode for the test state.
    enum class Mode { ALL, OUTSTANDING, FAILING, PAST_FAILING }

    // These are the files that are allowed for tracking test status.
    enum class StatusFile { SUCCESS, FAILURE, PAST_FAILURE }

    fun getRerunMode(project: Project) : Mode? {
      val prop = project.property(MODE_PROPERTY) ?: return null
      return when (prop.toString().lowercase()) {
        "all" -> Mode.ALL
        "failing" -> Mode.FAILING
        "past-failing" -> Mode.PAST_FAILING
        "past_failing" -> Mode.PAST_FAILING
        "outstanding" -> Mode.OUTSTANDING
        else -> null
      }
    }

    fun setUpTestingState(task: Test) {
      // Both the path and the mode must be defined for the testing state to be active.
      val testingStatePath = task.project.property(PATH_PROPERTY) ?: return
      val testingStateMode = getRerunMode(task.project) ?: return

      val projectName = task.project.name
      val indexDir = File(testingStatePath.toString())
      val reportDir = indexDir.resolve(projectName)
      val index = indexDir.resolve("index.html")
      val resuming = reportDir.exists()
      if (resuming) {
        applyTestFilters(testingStateMode, task, reportDir)
      }
      addTestHandler(task, projectName, index, reportDir)
    }

    private fun applyTestFilters(mode: Mode, task: Test, reportDir: File) {
      if (mode == Mode.ALL) {
        // Running without filters will (re)run all tests.
        return
      }
      if (mode == Mode.OUTSTANDING) {
        task.logger.lifecycle(
          "Note: the building of an exclude list often times out."
            + "You may need to simply rerun all tests.")
        forEachTestReportStatusMatching(task, reportDir, StatusFile.SUCCESS, { clazz, name ->
          task.filter.excludeTestsMatching("$clazz.$name")
        })
        return
      }
      assert(mode == Mode.FAILING || mode == Mode.PAST_FAILING)
      val result = if (mode == Mode.FAILING) StatusFile.FAILURE else StatusFile.PAST_FAILURE
      forEachTestReportStatusMatching(task, reportDir, result, { clazz, name ->
        task.filter.includeTestsMatching("$clazz.$name")
      })
    }

    private fun addTestHandler(
      task: Test,
      projectName: String,
      index: File,
      reportDir: File) {
      task.addTestOutputListener(object : TestOutputListener {
        override fun onOutput(desc: TestDescriptor, event: TestOutputEvent) {
          withTestResultEntryWriter(reportDir, desc, event.getDestination().name, true, {
            it.append(event.getMessage())
          })
        }
      })
      task.addTestListener(object : TestListener {

        override fun beforeSuite(desc: TestDescriptor) {}

        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
          if (desc.parent != null) {
            return
          }
          // Update the final test results in the index.
          val text = StringBuilder()
          val successColor = "#a2ff99"
          val failureColor = "#ff6454"
          val emptyColor = "#d4d4d4"
          val color: String;
          if (result.testCount == 0L) {
            color = emptyColor
          } else if (result.resultType == TestResult.ResultType.SUCCESS) {
            color = successColor
          } else if (result.resultType == TestResult.ResultType.FAILURE) {
            color = failureColor
          } else {
            color = failureColor
          }
          // The failure list has an open <ul> so close it before appending the module results.
          text.append("</ul>")
          text.append("<div style=\"background-color:${color}\">")
          text.append("<h2>${projectName}: ${result.resultType.name}</h2>")
          text.append("<ul>")
          text.append("<li>Number of tests: ${result.testCount}")
          text.append("<li>Failing tests: ${result.failedTestCount}")
          text.append("<li>Successful tests: ${result.successfulTestCount}")
          text.append("<li>Skipped tests: ${result.skippedTestCount}")
          text.append("</ul></div>")
          // Reopen a <ul> as other modules may still append test failures.
          text.append("<ul>")

          index.appendText(text.toString())
        }

        override fun beforeTest(desc: TestDescriptor) {
          // Remove any stale output files before running the test.
          for (destType in TestOutputEvent.Destination.values()) {
            val destFile = getTestResultEntryOutputFile(reportDir, desc, destType.name)
            if (destFile.exists()) {
              destFile.delete()
            }
          }
        }

        override fun afterTest(desc: TestDescriptor, result: TestResult) {
          if (result.testCount != 1L) {
            throw IllegalStateException("Unexpected test with more than one result: ${desc}")
          }
          updateStatusFiles(reportDir, desc, result.resultType)
          // Emit the test time.
          withTestResultEntryWriter(reportDir, desc, "time", false, {
            it.append("${result.getEndTime() - result.getStartTime()}")
          })
          // For failed tests, update the index and emit stack trace information.
          if (result.resultType == ResultType.FAILURE) {
            val title = testLinkContent(desc)
            val link = getTestReportEntryURL(reportDir, desc)
            index.appendText("<li><a href=\"${link}\">${title}</a></li>")
            if (!result.exceptions.isEmpty()) {
              printAllStackTracesToFile(
                result.exceptions,
                getTestResultEntryOutputFile(
                  reportDir,
                  desc,
                  "exceptions-raw.txt"
                )
              )
              // The raw stacktrace has lots of useless gradle test runner frames.
              // As a convenience filter out those so the stack is just easier to read.
              filterStackTraces(result)
              printAllStackTracesToFile(
                result.exceptions,
                getTestResultEntryOutputFile(
                  reportDir,
                  desc,
                  "exceptions-filtered.txt"
                )
              )
            }
          }
        }
      })
    }

    private fun getStatusFile(result: ResultType) : StatusFile {
      return when (result) {
        ResultType.FAILURE -> StatusFile.FAILURE
        ResultType.SUCCESS -> StatusFile.SUCCESS
        ResultType.SKIPPED -> StatusFile.SUCCESS
      }
    }

    private fun escapeHtml(string: String): String {
      return string
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    }

    private fun urlEncode(string: String): String {
      // Not sure why, but the + also needs to be converted to have working links.
      return URLEncoder.encode(string, "UTF-8").replace("+", "%20")
    }

    private fun testLinkContent(desc: TestDescriptor) : String {
      val pkgR8 = "com.android.tools.r8."
      val className = desc.className!!
      val shortClassName =
        if (className.startsWith(pkgR8)) {
          className.substring(pkgR8.length)
        } else {
          className
        }
      return escapeHtml("${shortClassName}.${desc.name}")
    }

    private fun filterStackTraces(result: TestResult) {
      for (throwable in result.getExceptions()) {
        filterStackTrace(throwable)
      }
    }

    // It would be nice to do this in a non-destructive way...
    private fun filterStackTrace(exception: Throwable) {
      val elements = ArrayList<StackTraceElement>()
      val skipped = ArrayList<StackTraceElement>()
      for (element in exception.getStackTrace()) {
        if (element.toString().contains("com.android.tools.r8")) {
          elements.addAll(skipped)
          elements.add(element)
          skipped.clear()
        } else {
          skipped.add(element)
        }
      }
      exception.setStackTrace(elements.toTypedArray())
    }

    private fun printAllStackTracesToFile(exceptions: List<Throwable>, out: File) {
      PrintStream(FileOutputStream(out))
        .use({ printer -> exceptions.forEach { it.printStackTrace(printer) } })
    }

    private fun ensureDir(dir: File): File {
      dir.mkdirs()
      return dir
    }

    // Some of our test parameters have new lines :-( We really don't want test names to span lines.
    private fun sanitizedTestName(testName: String): String {
      if (testName.contains("\n")) {
        throw RuntimeException("Unsupported use of newline in test name: '${testName}'")
      }
      return testName
    }

    private fun getTestReportClassDirPath(reportDir: File, testClass: String): Path {
      return reportDir.toPath().resolve(testClass)
    }

    private fun getTestReportEntryDirFromString(reportDir: File, testClass: String, testName: String): File {
      return ensureDir(
        getTestReportClassDirPath(reportDir, testClass)
          .resolve(sanitizedTestName(testName))
          .toFile())
    }

    private fun getTestReportEntryDirFromTest(reportDir: File, testDesc: TestDescriptor): File {
      return getTestReportEntryDirFromString(reportDir, testDesc.className!!, testDesc.name)
    }

    private fun getTestReportEntryURL(reportDir: File, testDesc: TestDescriptor): Path {
      val classDir = urlEncode(testDesc.className!!)
      val testDir = urlEncode(sanitizedTestName(testDesc.name))
      return reportDir.toPath().resolve(classDir).resolve(testDir)
    }

    private fun getTestResultEntryOutputFile(
      reportDir: File,
      testDesc: TestDescriptor,
      fileName: String
    ): File {
      val dir = getTestReportEntryDirFromTest(reportDir, testDesc).toPath()
      return dir.resolve(fileName).toFile()
    }

    private fun updateStatusFiles(
      reportDir: File,
      desc: TestDescriptor,
      result: ResultType) {
      val statusFile = getStatusFile(result)
      withTestResultEntryWriter(reportDir, desc, statusFile.name, false, {
        it.append(statusFile.name)
      })
      if (statusFile == StatusFile.FAILURE) {
        getTestResultEntryOutputFile(reportDir, desc, StatusFile.SUCCESS.name).delete()
        val pastFailure = StatusFile.PAST_FAILURE.name
        withTestResultEntryWriter(reportDir, desc, pastFailure, false, {
          it.append(pastFailure)
        })
      } else {
        getTestResultEntryOutputFile(reportDir, desc, StatusFile.FAILURE.name).delete()
      }
    }

    private fun withTestResultEntryWriter(
      reportDir: File,
      testDesc: TestDescriptor,
      fileName: String,
      append: Boolean,
      fn: (FileWriter) -> Unit
    ) {
      val file = getTestResultEntryOutputFile(reportDir, testDesc, fileName)
      FileWriter(file, append).use(fn)
    }

    fun forEachTestReportStatusMatching(
      test: Test,
      reportDir: File,
      statusFile: StatusFile,
      onTest: (String, String) -> Unit
    ): Boolean {
      val fileName = statusFile.name
      val logger = test.logger
      val proc = ProcessBuilder("find", ".", "-name", fileName)
        .directory(reportDir)
        .redirectOutput(ProcessBuilder.Redirect.PIPE)
        .start()
      val result = proc.waitFor(10, TimeUnit.SECONDS)
      if (!result) {
        throw RuntimeException("Unexpected failure to find reports within time limit")
      }
      var hadMatch = false
      for (rawLine in proc.inputStream.bufferedReader().lineSequence()) {
        // Lines are of the form: ./<class>/<name>/FAILURE
        try {
          val trimmed = rawLine.trim()
          val line = trimmed.substring(2)
          val sep = line.indexOf("/")
          val clazz = line.substring(0, sep)
          val name = line.substring(sep + 1, line.length - fileName.length - 1)
          onTest(clazz, name)
          hadMatch = true
        } catch (e: Exception) {
          logger.lifecycle("WARNING: failed attempt to read test description from: '${rawLine}'")
        }
      }
      return hadMatch
    }
  }
}