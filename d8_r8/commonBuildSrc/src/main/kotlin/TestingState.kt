// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintStream
import java.net.URLEncoder
import java.nio.file.Path
import org.gradle.api.Project
import org.gradle.api.logging.Logger
import org.gradle.api.tasks.Exec
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.kotlin.dsl.register

// Utility to install tracking of test results in status files.
class TestingState {
  companion object {

    const val MODE_PROPERTY = "testing-state-mode"
    const val PATH_PROPERTY = "testing-state-path"

    // Operating mode for the test state.
    enum class Mode { ALL, OUTSTANDING, FAILING, PAST_FAILING }

    // These are the files that are allowed for tracking test status.
    enum class StatusType { SUCCESS, FAILURE, PAST_FAILURE }

    fun getRerunMode(project: Project) : Mode? {
      val prop = project.findProperty(MODE_PROPERTY) ?: return null
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
      val testingStatePath = task.project.findProperty(PATH_PROPERTY) ?: return
      val testingStateMode = getRerunMode(task.project) ?: return

      val projectName = task.project.name
      val indexDir = File(testingStatePath.toString())
      val reportDir = indexDir.resolve(projectName)
      val index = indexDir.resolve("index.html")
      val resuming = reportDir.exists()
      if (resuming) {
        applyTestFilters(testingStateMode, task, reportDir, indexDir, projectName)
      }
      addTestHandler(task, projectName, index, reportDir)
    }

    private fun applyTestFilters(
      mode: Mode,
      task: Test,
      reportDir: File,
      indexDir: File,
      projectName: String,
    ) {
      if (mode == Mode.ALL) {
        // Running without filters will (re)run all tests.
        return
      }
      val statusType = getStatusTypeForMode(mode)
      val statusOutputFile = indexDir.resolve("${projectName}.${statusType.name}.txt")
      val findStatusTask = task.project.tasks.register<Exec>("${projectName}-find-status-files")
      {
        inputs.dir(reportDir)
        outputs.file(statusOutputFile)
        workingDir(reportDir)
        commandLine(
          "find", ".", "-name", statusType.name
        )
        doFirst {
          standardOutput = statusOutputFile.outputStream()
        }
      }
      task.dependsOn(findStatusTask)
      task.doFirst {
        if (mode == Mode.OUTSTANDING) {
          forEachTestReportStatusMatching(
            statusType,
            findStatusTask.get().outputs.files.singleFile,
            task.logger,
            { clazz, name -> task.filter.excludeTestsMatching("${clazz}.${name}") })
        } else {
          val hasMatch = forEachTestReportStatusMatching(
            statusType,
            findStatusTask.get().outputs.files.singleFile,
            task.logger,
            { clazz, name -> task.filter.includeTestsMatching("${clazz}.${name}") })
          if (!hasMatch) {
            // Add a filter that does not match to ensure the test run is not "without filters"
            // which would run all tests.
            task.filter.includeTestsMatching("NON_MATCHING_TEST_FILTER")
          }
        }
      }
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

    private fun getStatusTypeForMode(mode: Mode) : StatusType {
      return when (mode) {
        Mode.OUTSTANDING -> StatusType.SUCCESS
        Mode.FAILING -> StatusType.FAILURE
        Mode.PAST_FAILING -> StatusType.PAST_FAILURE
        Mode.ALL -> throw RuntimeException("Unexpected mode 'all' in status determination")
      }
    }

    private fun getStatusTypeForResult(result: ResultType) : StatusType {
      return when (result) {
        ResultType.FAILURE -> StatusType.FAILURE
        ResultType.SUCCESS -> StatusType.SUCCESS
        ResultType.SKIPPED -> StatusType.SUCCESS
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
      val statusFile = getStatusTypeForResult(result)
      withTestResultEntryWriter(reportDir, desc, statusFile.name, false, {
        it.append(statusFile.name)
      })
      if (statusFile == StatusType.FAILURE) {
        getTestResultEntryOutputFile(reportDir, desc, StatusType.SUCCESS.name).delete()
        val pastFailure = StatusType.PAST_FAILURE.name
        withTestResultEntryWriter(reportDir, desc, pastFailure, false, {
          it.append(pastFailure)
        })
      } else {
        getTestResultEntryOutputFile(reportDir, desc, StatusType.FAILURE.name).delete()
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

    private fun forEachTestReportStatusMatching(
      type: StatusType, file: File, logger: Logger, onTest: (String, String) -> Unit
    ) : Boolean {
      val fileName = type.name
      var hasMatch = false
      for (rawLine in file.bufferedReader().lineSequence()) {
        // Lines are of the form: ./<class>/<name>/<mode>
        try {
          val trimmed = rawLine.trim()
          val line = trimmed.substring(2)
          val sep = line.indexOf("/")
          val clazz = line.substring(0, sep)
          val name = line.substring(sep + 1, line.length - fileName.length - 1)
          onTest(clazz, name)
          hasMatch = true
        } catch (e: Exception) {
          logger.lifecycle("WARNING: failed attempt to read test description from: '${rawLine}'")
        }
      }
      return hasMatch
    }
  }
}