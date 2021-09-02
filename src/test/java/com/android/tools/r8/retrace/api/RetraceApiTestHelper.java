// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.api;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.Collectors;
import com.android.tools.r8.TestRuntime.CfRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.InnerClassPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.rules.TemporaryFolder;

public class RetraceApiTestHelper {

  private static final String JUNIT_JAR = "junit-4.13-beta-2.jar";
  private static final String HAMCREST = "hamcrest-core-1.3.jar";

  public static List<Class<? extends RetraceApiBinaryTest>> CLASSES_FOR_BINARY_COMPATIBILITY =
      ImmutableList.of(RetraceApiEmptyTest.RetraceTest.class);
  public static List<Class<? extends RetraceApiBinaryTest>> CLASSES_PENDING_BINARY_COMPATIBILITY =
      ImmutableList.of();

  public static void runJunitOnTests(
      CfRuntime runtime,
      Path r8Jar,
      Class<? extends RetraceApiBinaryTest> clazz,
      TemporaryFolder temp)
      throws Exception {
    assertTrue(testIsSpecifiedAsBinaryOrPending(clazz));
    List<Class<? extends RetraceApiBinaryTest>> testClasses = ImmutableList.of(clazz);
    runJunitOnTests(
        runtime, r8Jar, generateJarForRetraceBinaryTests(temp, testClasses), testClasses);
  }

  public static void runJunitOnTests(
      CfRuntime runtime,
      Path r8Jar,
      Path testJar,
      Collection<Class<? extends RetraceApiBinaryTest>> tests)
      throws Exception {
    List<Path> classPaths = ImmutableList.of(getJunitDependency(), getHamcrest(), r8Jar, testJar);
    ProcessResult processResult =
        ToolHelper.runJava(
            runtime,
            classPaths,
            "org.junit.runner.JUnitCore",
            StringUtils.join(" ", tests, Class::getTypeName));
    assertEquals(0, processResult.exitCode);
    assertThat(processResult.stdout, containsString("OK (" + tests.size() + " test"));
  }

  private static Path getJunitDependency() {
    String junitPath =
        Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(cp -> cp.endsWith(JUNIT_JAR))
            .collect(Collectors.toSingle());
    return Paths.get(junitPath);
  }

  private static Path getHamcrest() {
    String junitPath =
        Arrays.stream(System.getProperty("java.class.path").split(File.pathSeparator))
            .filter(cp -> cp.endsWith(HAMCREST))
            .collect(Collectors.toSingle());
    return Paths.get(junitPath);
  }

  public static Path generateJarForRetraceBinaryTests(
      TemporaryFolder temp, Collection<Class<? extends RetraceApiBinaryTest>> classes)
      throws Exception {
    Path jar = File.createTempFile("retrace_api_tests", ".jar", temp.getRoot()).toPath();
    ZipBuilder zipBuilder = ZipBuilder.builder(jar);
    for (Class<? extends RetraceApiBinaryTest> retraceApiTest : classes) {
      zipBuilder.addFilesRelative(
          ToolHelper.getClassPathForTests(),
          ToolHelper.getClassFilesForInnerClasses(retraceApiTest));
      zipBuilder.addBytes(
          ZipUtils.zipEntryNameForClass(retraceApiTest),
          ClassFileTransformer.create(retraceApiTest)
              .removeInnerClasses(
                  InnerClassPredicate.onName(
                      DescriptorUtils.getBinaryNameFromJavaType(retraceApiTest.getTypeName())))
              .transform());
    }
    zipBuilder.addFilesRelative(
        ToolHelper.getClassPathForTests(),
        ToolHelper.getClassFileForTestClass(RetraceApiBinaryTest.class));
    return zipBuilder.build();
  }

  public static Collection<Class<? extends RetraceApiBinaryTest>> getBinaryCompatibilityTests() {
    return CLASSES_FOR_BINARY_COMPATIBILITY;
  }

  private static boolean testIsSpecifiedAsBinaryOrPending(Class<?> clazz) {
    return CLASSES_FOR_BINARY_COMPATIBILITY.contains(clazz)
        || CLASSES_PENDING_BINARY_COMPATIBILITY.contains(clazz);
  }
}
