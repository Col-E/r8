// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import java.util.Date
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestResult

class TestConfigurationHelper {

  companion object {

    fun setupTestTask(test: Test) {
      val project = test.project
      test.systemProperty("USE_NEW_GRADLE_SETUP", "true")
      if (project.hasProperty("kotlin_compiler_dev")) {
        test.systemProperty("com.android.tools.r8.kotlincompilerdev", "1")
      }

      if (project.hasProperty("kotlin_compiler_old")) {
        test.systemProperty("com.android.tools.r8.kotlincompilerold", "1")
      }

      if (project.hasProperty("dex_vm")
          && project.property("dex_vm") != "default") {
        println("NOTE: Running with non default vm: " + project.property("dex_vm"))
        test.systemProperty("dex_vm", project.property("dex_vm")!!)
      }

      // Forward runtime configurations for test parameters.
      if (project.hasProperty("runtimes")) {
        println("NOTE: Running with runtimes: " + project.property("runtimes"))
        test.systemProperty("runtimes", project.property("runtimes")!!)
      }

      if (project.hasProperty("art_profile_rewriting_completeness_check")) {
        test.systemProperty(
          "com.android.tools.r8.artprofilerewritingcompletenesscheck",
          project.property("art_profile_rewriting_completeness_check")!!)
      }

      if (project.hasProperty("disable_assertions")) {
        test.enableAssertions = false
      }

      // Forward project properties into system properties.
      listOf(
        "slow_tests",
        "desugar_jdk_json_dir",
        "desugar_jdk_libs",
        "test_dir",
        "command_cache_dir").forEach {
        if (project.hasProperty(it)) {
          project.property(it)?.let { v -> test.systemProperty("slow_tests", v) }
        }
      }

      if (project.hasProperty("no_internal")) {
        test.exclude("com/android/tools/r8/internal/**")
      }
      if (project.hasProperty("only_internal")) {
        test.include("com/android/tools/r8/internal/**")
      }
      if (project.hasProperty("no_arttests")) {
        test.exclude("com/android/tools/r8/art/**")
      }

      if (project.hasProperty("test_xmx")) {
        test.maxHeapSize = project.property("test_xmx")!!.toString()
      } else {
        test.maxHeapSize = "4G"
      }

      if (project.hasProperty("one_line_per_test")
        || project.hasProperty("update_test_timestamp")) {
        test.addTestListener(object : TestListener {
          override fun beforeSuite(desc: TestDescriptor?) {}
          override fun afterSuite(desc: TestDescriptor?, result: TestResult?) {}
          override fun beforeTest(desc: TestDescriptor?) {
            if (project.hasProperty("one_line_per_test")) {
              println("Start executing ${desc}")
            }
          }

          override fun afterTest(desc: TestDescriptor?, result: TestResult?) {
            if (project.hasProperty("one_line_per_test")) {
              println("Done executing ${desc} with result: ${result?.resultType}")
            }
            if (project.hasProperty("update_test_timestamp")) {
              File(project.property("update_test_timestamp")!!.toString())
                .writeText(Date().getTime().toString())
            }
          }
        })
      }

      val userDefinedCoresPerFork = System.getenv("R8_GRADLE_CORES_PER_FORK")
      val processors = Runtime.getRuntime().availableProcessors()
      // See https://docs.gradle.org/current/dsl/org.gradle.api.tasks.testing.Test.html.
      if (!userDefinedCoresPerFork.isNullOrEmpty()) {
        test.maxParallelForks = processors.div(userDefinedCoresPerFork.toInt())
      } else {
        // On work machines this seems to give the best test execution time (without freezing).
        test.maxParallelForks = processors.div(3)
        // On low cpu count machines (bots) we under subscribe, so increase the count.
        if (processors == 32) {
          test.maxParallelForks = 15
        }
      }
    }
  }
}
