// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi;

import static com.android.tools.r8.ToolHelper.TestDataSourceSet.computeLegacyOrGradleSpecifiedLocation;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRuntime;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ToolHelper.TestDataSourceSet;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.transformers.ClassFileTransformer.InnerClassPredicate;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.ZipUtils.ZipBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Abstract base to define a collection of API tests that should be run against a checked in jar.
 */
public abstract class BinaryCompatibilityTestCollection<T> {

  /** Jar to run tests against. */
  public abstract Path getTargetJar();

  /** Jar with tests. */
  public abstract Path getCheckedInTestJar();

  /** List of classes that make up the checked in test suite. */
  public abstract List<Class<? extends T>> getCheckedInTestClasses();

  /** List of classes that are yet to become part of the test suite. */
  public abstract List<Class<? extends T>> getPendingTestClasses();

  /** Additional classes that should always be included together to run tests. */
  public abstract List<Class<?>> getAdditionalClassesForTests();

  /** Additional classes that should always be included together to run the pending tests. */
  public abstract List<Class<?>> getPendingAdditionalClassesForTests();

  /** Additional JVM args supplied to any external execution. */
  public abstract List<String> getVmArgs();

  /** Temporary folder for generating jars and so on. */
  public abstract TemporaryFolder getTemp();

  public String makeProperty(String key, String value) {
    return "-D" + key + "=" + value;
  }

  private void verifyConsistency() {
    assertEquals(
        ImmutableSet.of(),
        Sets.intersection(
            ImmutableSet.copyOf(getCheckedInTestClasses()),
            ImmutableSet.copyOf(getPendingTestClasses())));
  }

  private boolean testIsCheckedInOrPending(Class<?> clazz) {
    return getCheckedInTestClasses().contains(clazz) || getPendingTestClasses().contains(clazz);
  }

  public void runJunitOnCheckedInJar() throws Exception {
    runJunitOnTestClasses(getCheckedInTestJar(), getCheckedInTestClasses());
  }

  public void runJunitOnTestClass(Class<? extends T> test) throws Exception {
    List<Class<? extends T>> testClasses = Collections.singletonList(test);
    runJunitOnTestClasses(
        generateJarForTestClasses(
            computeLegacyOrGradleSpecifiedLocation(),
            testClasses,
            getPendingAdditionalClassesForTests()),
        testClasses);
  }

  private void runJunitOnTestClasses(Path testJar, Collection<Class<? extends T>> tests)
      throws Exception {
    verifyConsistency();
    IntBox numberOfTestMethods = new IntBox(0);
    List<Path> classPaths =
        ImmutableList.of(getJunitDependency(), getHamcrest(), getTargetJar(), testJar);
    List<String> args = new ArrayList<>();
    args.add("org.junit.runner.JUnitCore");
    tests.forEach(
        test -> {
          assertTrue(testIsCheckedInOrPending(test));
          args.add(test.getTypeName());
          for (Method method : test.getDeclaredMethods()) {
            if (method.isAnnotationPresent(Test.class)) {
              numberOfTestMethods.increment();
            }
          }
        });
    ProcessResult processResult =
        ToolHelper.runJava(
            TestRuntime.getSystemRuntime(),
            ImmutableList.<String>builder().add("-ea").addAll(getVmArgs()).build(),
            classPaths,
            args.toArray(new String[0]));
    assertEquals(processResult.toString(), 0, processResult.exitCode);
    assertThat(processResult.stdout, containsString("OK (" + numberOfTestMethods.get() + " test"));
  }

  private static Path getJunitDependency() {
    return ToolHelper.getJunitFromDeps();
  }

  private static Path getHamcrest() {
    return ToolHelper.getHamcrestFromDeps();
  }

  public Path generateJarForCheckedInTestClasses(TestDataSourceSet testDataSourceSet)
      throws Exception {
    return generateJarForTestClasses(
        testDataSourceSet, getCheckedInTestClasses(), Collections.emptyList());
  }

  private Path generateJarForTestClasses(
      TestDataSourceSet testDataSourceSet,
      Collection<Class<? extends T>> classes,
      List<Class<?>> additionalPendingClassesForTest)
      throws Exception {
    Path jar = getTemp().newFolder().toPath().resolve("test.jar");
    ZipBuilder zipBuilder = ZipBuilder.builder(jar);
    for (Class<? extends T> test : classes) {
      zipBuilder.addFilesRelative(
          ToolHelper.getClassPathForTestDataSourceSet(testDataSourceSet),
          ToolHelper.getClassFilesForInnerClasses(testDataSourceSet, test));
      zipBuilder.addBytes(
          ZipUtils.zipEntryNameForClass(test),
          ClassFileTransformer.create(test, testDataSourceSet)
              .removeInnerClasses(
                  InnerClassPredicate.onName(
                      DescriptorUtils.getBinaryNameFromJavaType(test.getTypeName())))
              .transform());
    }
    zipBuilder.addFilesRelative(
        ToolHelper.getClassPathForTestDataSourceSet(testDataSourceSet),
        getAdditionalClassesForTests().stream()
            .map(clazz -> ToolHelper.getClassFileForTestClass(clazz, testDataSourceSet))
            .collect(Collectors.toList()));
    zipBuilder.addFilesRelative(
        ToolHelper.getClassPathForTestDataSourceSet(testDataSourceSet),
        additionalPendingClassesForTest.stream()
            .map(clazz -> ToolHelper.getClassFileForTestClass(clazz, testDataSourceSet))
            .collect(Collectors.toList()));
    return zipBuilder.build();
  }

  public void verifyCheckedInJarIsUpToDate() throws Exception {
    TemporaryFolder temp = getTemp();
    Path checkedInContents = temp.newFolder().toPath();
    Path generatedContents = temp.newFolder().toPath();
    ZipUtils.unzip(getCheckedInTestJar(), checkedInContents);
    ZipUtils.unzip(
        generateJarForCheckedInTestClasses(computeLegacyOrGradleSpecifiedLocation()),
        generatedContents);
    try (Stream<Path> existingPaths = Files.walk(checkedInContents);
        Stream<Path> generatedPaths = Files.walk(generatedContents)) {
      List<Path> existing =
          existingPaths.filter(FileUtils::isClassFile).collect(Collectors.toList());
      List<Path> generated =
          generatedPaths.filter(FileUtils::isClassFile).collect(Collectors.toList());
      for (Path classFile : generated) {
        Path otherClassFile = checkedInContents.resolve(generatedContents.relativize(classFile));
        assertTrue("Could not find file: " + otherClassFile, Files.exists(otherClassFile));
        assertTrue(
            "Non-equal files: " + otherClassFile,
            TestBase.filesAreEqual(classFile, otherClassFile));
      }
      assertEquals(existing.size(), generated.size());
      assertNotEquals(0, existing.size());
    }
  }

  public void replaceJarForCheckedInTestClasses(TestDataSourceSet sourceSet) throws Exception {
    Path checkedInJar = getCheckedInTestJar();
    Path tarballDir = checkedInJar.getParent();
    Path parentDir = tarballDir.getParent();
    if (!Files.exists(Paths.get(tarballDir + ".tar.gz.sha1"))) {
      throw new RuntimeException("Could not locate the SHA file for " + tarballDir);
    }
    Path generatedJar = generateJarForCheckedInTestClasses(sourceSet);
    Files.move(generatedJar, checkedInJar, StandardCopyOption.REPLACE_EXISTING);
    System.out.println(
        "Updated file in: "
            + checkedInJar
            + "\nRemember to upload to cloud storage:"
            + "\n(cd "
            + parentDir
            + " && upload_to_google_storage.py -a --bucket r8-deps "
            + tarballDir.getFileName()
            + ")");
  }
}
