// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.PrintStream
import java.net.URLEncoder
import java.nio.file.Path
import java.util.Date
import java.util.concurrent.TimeUnit
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult

class TestingState {
  companion object {

    fun setUpTestingState(task: Test) {
      val project = task.project
      val reportDir = File(project.property("testing-state")!!.toString())
      val index = reportDir.resolve("index.html")
      val reportDirExists = reportDir.exists()
      val resuming = reportDirExists

      var hasFailingTests = false
      if (resuming) {
        // Test filtering happens before the test execution is initiated so compute it here.
        // If there are still failing tests in the report, include only those.
        hasFailingTests = forEachTestReportAlreadyFailing(task, reportDir, { clazz, name ->
          task.filter.includeTestsMatching("$clazz.$name")
        })
        // Otherwise exclude all of the test already marked as succeeding.
        if (!hasFailingTests) {
          // Also allow the test to overall succeed if there are no remaining tests that match,
          // which is natural if the state already succeeded in full.
          task.filter.isFailOnNoMatchingTests = false
          forEachTestReportAlreadyPassing(task, reportDir, { clazz, name ->
            task.filter.excludeTestsMatching("$clazz.$name")
          })
          forEachTestReportAlreadySkipped(task, reportDir, { clazz, name ->
            task.filter.excludeTestsMatching("$clazz.$name")
          })
        }
      }

      task.addTestListener(object : TestListener {
        fun isRoot(desc: TestDescriptor): Boolean {
          return desc.parent == null
        }

        fun getFreshTestReportIndex(reportDir: File): File {
          var number = 0
          while (true) {
            val freshIndex = reportDir.toPath().resolve("index.${number++}.html").toFile()
            if (!freshIndex.exists()) {
              return freshIndex
            }
          }
        }

        fun escapeHtml(string: String): String {
          return string.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        }

        fun filterStackTraces(result: TestResult) {
          for (throwable in result.getExceptions()) {
            filterStackTrace(throwable)
          }
        }

        // It would be nice to do this in a non-destructive way...
        fun filterStackTrace(exception: Throwable) {
          if (!project.hasProperty("print_full_stacktraces")) {
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
        }

        fun printAllStackTracesToFile(exceptions: List<Throwable>, out: File) {
          PrintStream(FileOutputStream(out))
            .use({ printer -> exceptions.forEach { it.printStackTrace(printer) } })
        }

        override fun beforeSuite(desc: TestDescriptor) {
          if (!isRoot(desc)) {
            return
          }
          var parentReport: File? = null
          if (resuming) {
            if (index.exists()) {
              parentReport = getFreshTestReportIndex(reportDir)
              index.renameTo(parentReport)
            }
          } else {
            reportDir.mkdirs()
          }
          val runPrefix = if (resuming) "Resuming" else "Starting"
          val title = "${runPrefix} @ ${reportDir}"
          // Print a console link to the test report for easy access.
          println("${runPrefix} test, report written to:")
          println("  file://${index}")
          // Print the new index content.
          index.appendText("<html><head><title>${title}</title>")
          index.appendText("<style> * { font-family: monospace; }</style>")
          index.appendText("<meta http-equiv='refresh' content='10' />")
          index.appendText("</head><body><h1>${title}</h1>")
          index.appendText("<p>Run on: ${Date()}</p>")
          if (parentReport != null) {
            index.appendText("<p><a href=\"file://${parentReport}\">Previous result index</a></p>")
          }
          index.appendText("<p><a href=\"file://${index}\">Most recent result index</a></p>")
          index.appendText("<p><a href=\"file://${reportDir}\">Test directories</a></p>")
          index.appendText("<h2>Failing tests (refreshing automatically every 10 seconds)</h2><ul>")
        }

        override fun afterSuite(desc: TestDescriptor, result: TestResult) {
          if (!isRoot(desc)) {
            return
          }
          // Update the final test results in the index.
          index.appendText("</ul>")
          if (result.resultType == TestResult.ResultType.SUCCESS) {
            if (hasFailingTests) {
              index.appendText("<h2>Rerun of failed tests now pass!</h2>")
              index.appendText("<h2>Rerun again to continue with outstanding tests!</h2>")
            } else {
              index.appendText("<h2 style=\"background-color:#62D856\">GREEN BAR == YOU ROCK!</h2>")
            }
          } else if (result.resultType == TestResult.ResultType.FAILURE) {
            index.appendText("<h2 style=\"background-color:#6D130A\">Some tests failed: ${result.resultType.name}</h2><ul>")
          } else {
            index.appendText("<h2>Tests finished: ${result.resultType.name}</h2><ul>")
          }
          index.appendText("<li>Number of tests: ${result.testCount}")
          index.appendText("<li>Failing tests: ${result.failedTestCount}")
          index.appendText("<li>Successful tests: ${result.successfulTestCount}")
          index.appendText("<li>Skipped tests: ${result.skippedTestCount}")
          index.appendText("</ul></body></html>")
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
          // Clear any previous result files.
          for (resultType in TestResult.ResultType.values()) {
            getTestResultEntryOutputFile(reportDir, desc, resultType.name).delete()
          }
          // Emit the result type status in a file of the same name: SUCCESS, FAILURE or SKIPPED.
          withTestResultEntryWriter(reportDir, desc, result.getResultType().name, false, {
            it.append(result.getResultType().name)
          })
          // Emit the test time.
          withTestResultEntryWriter(reportDir, desc, "time", false, {
            it.append("${result.getEndTime() - result.getStartTime()}")
          })
          // For failed tests, update the index and emit stack trace information.
          if (result.resultType == TestResult.ResultType.FAILURE) {
            val title = escapeHtml("${desc.className}.${desc.name}")
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

      task.addTestOutputListener(object : TestOutputListener {
        override fun onOutput(desc: TestDescriptor, event: TestOutputEvent) {
          withTestResultEntryWriter(reportDir, desc, event.getDestination().name, true, {
            it.append(event.getMessage())
          })
        }
      })
    }

    fun urlEncode(string: String): String {
      // Not sure why, but the + also needs to be converted to have working links.
      return URLEncoder.encode(string, "UTF-8").replace("+", "%20")
    }

    fun ensureDir(dir: File): File {
      dir.mkdirs()
      return dir
    }

    // Some of our test parameters have new lines :-( We really don't want test names to span lines.
    fun sanitizedTestName(testDesc: TestDescriptor): String {
      if (testDesc.getName().contains("\n")) {
        throw RuntimeException("Unsupported use of newline in test name: '${testDesc.getName()}'")
      }
      return testDesc.getName()
    }

    fun getTestReportEntryDir(reportDir: File, testDesc: TestDescriptor): File {
      return ensureDir(
        reportDir.toPath()
          .resolve(testDesc.getClassName()!!)
          .resolve(sanitizedTestName(testDesc))
          .toFile()
      )
    }

    fun getTestReportEntryURL(reportDir: File, testDesc: TestDescriptor): Path {
      val classDir = urlEncode(testDesc.getClassName()!!)
      val testDir = urlEncode(sanitizedTestName(testDesc))
      return reportDir.toPath().resolve(classDir).resolve(testDir)
    }

    fun getTestResultEntryOutputFile(
      reportDir: File,
      testDesc: TestDescriptor,
      fileName: String
    ): File {
      val dir = getTestReportEntryDir(reportDir, testDesc).toPath()
      return dir.resolve(fileName).toFile()
    }

    fun withTestResultEntryWriter(
      reportDir: File,
      testDesc: TestDescriptor,
      fileName: String,
      append: Boolean,
      fn: (FileWriter) -> Unit
    ) {
      val file = getTestResultEntryOutputFile(reportDir, testDesc, fileName)
      FileWriter(file, append).use(fn)
    }

    fun forEachTestReportAlreadyFailing(
      test: Test,
      reportDir: File,
      onFailureTest: (String, String) -> Unit
    ): Boolean {
      return internalForEachTestReportState(
        test,
        reportDir,
        TestResult.ResultType.FAILURE.name,
        onFailureTest
      )
    }

    fun forEachTestReportAlreadyPassing(
      test: Test,
      reportDir: File,
      onSucceededTest: (String, String) -> Unit
    ): Boolean {
      return internalForEachTestReportState(
        test,
        reportDir,
        TestResult.ResultType.SUCCESS.name,
        onSucceededTest
      )
    }

    fun forEachTestReportAlreadySkipped(
      test: Test,
      reportDir: File,
      onSucceededTest: (String, String) -> Unit
    ): Boolean {
      return internalForEachTestReportState(
        test,
        reportDir,
        TestResult.ResultType.SKIPPED.name,
        onSucceededTest
      )
    }

    fun internalForEachTestReportState(
      test: Test,
      reportDir: File,
      fileName: String,
      onTest: (String, String) -> Unit
    ): Boolean {
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