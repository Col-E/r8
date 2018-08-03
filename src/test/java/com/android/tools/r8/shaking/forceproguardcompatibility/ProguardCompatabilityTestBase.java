// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

public class ProguardCompatabilityTestBase extends TestBase {

  protected Path proguardMap;

  public enum Shrinker {
    PROGUARD5,
    PROGUARD6,
    PROGUARD6_THEN_D8,
    R8_COMPAT,
    R8
  }

  protected static boolean isR8(Shrinker shrinker) {
    return shrinker == Shrinker.R8_COMPAT || shrinker == Shrinker.R8;
  }

  protected AndroidApp runShrinker(
      Shrinker mode, List<Class> programClasses, Iterable<String> proguadConfigs) throws Exception {
    return runShrinker(mode, programClasses, String.join(System.lineSeparator(), proguadConfigs));
  }

  protected AndroidApp runShrinker(Shrinker mode, List<Class> programClasses, String proguardConfig)
      throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    switch (mode) {
      case PROGUARD5:
        return runProguard5(programClasses, proguardConfig, proguardMap);
      case PROGUARD6:
        return runProguard6(programClasses, proguardConfig, proguardMap);
      case PROGUARD6_THEN_D8:
        return runProguard6AndD8(programClasses, proguardConfig, proguardMap);
      case R8_COMPAT:
        return runR8Compat(programClasses, proguardConfig);
      case R8:
        return runR8(programClasses, proguardConfig);
    }
    throw new IllegalArgumentException("Unknown shrinker: " + mode);
  }

  protected CodeInspector inspectAfterShrinking(
      Shrinker mode, List<Class> programClasses, List<String> proguadConfigs) throws Exception {
    return inspectAfterShrinking(
        mode, programClasses, String.join(System.lineSeparator(), proguadConfigs));
  }

  protected CodeInspector inspectAfterShrinking(
      Shrinker mode, List<Class> programClasses, String proguardConfig) throws Exception {
    switch (mode) {
      case PROGUARD5:
        return inspectProguard5Result(programClasses, proguardConfig);
      case PROGUARD6:
        return inspectProguard6Result(programClasses, proguardConfig);
      case PROGUARD6_THEN_D8:
        return inspectProguard6AndD8Result(programClasses, proguardConfig);
      case R8_COMPAT:
        return inspectR8CompatResult(programClasses, proguardConfig);
      case R8:
        return inspectR8Result(programClasses, proguardConfig);
    }
    throw new IllegalArgumentException("Unknown shrinker: " + mode);
  }

  protected AndroidApp runR8(List<Class> programClasses, String proguardConfig) throws Exception {
    return runR8(programClasses, proguardConfig, null);
  }

  protected AndroidApp runR8(
      List<Class> programClasses, String proguardConfig, Consumer<InternalOptions> configure)
      throws Exception {
    AndroidApp app = readClassesAndAndriodJar(programClasses);
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app);
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown());
    return ToolHelper.runR8(builder.build(), configure);
  }

  protected CodeInspector inspectR8Result(List<Class> programClasses, String proguardConfig)
      throws Exception {
    return new CodeInspector(runR8(programClasses, proguardConfig));
  }

  protected AndroidApp runR8Compat(List<Class> programClasses, String proguardConfig)
      throws Exception {
    CompatProguardCommandBuilder builder = new CompatProguardCommandBuilder(true);
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown());
    programClasses.forEach(
        clazz -> builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz)));
    builder.addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    return ToolHelper.runR8(builder.build());
  }

  protected CodeInspector inspectR8CompatResult(List<Class> programClasses, String proguardConfig)
      throws Exception {
    return new CodeInspector(runR8Compat(programClasses, proguardConfig));
  }

  protected AndroidApp runProguard5(
      List<Class> programClasses, String proguardConfig, Path proguardMap) throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ProcessResult result = ToolHelper.runProguardRaw(
        jarTestClasses(programClasses),
        proguardedJar,
        ToolHelper.getAndroidJar(AndroidApiLevel.N),
        proguardConfigFile,
        proguardMap);
    if (result.exitCode != 0) {
      fail("Proguard failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    return readJar(proguardedJar);
  }

  protected CodeInspector inspectProguard5Result(List<Class> programClasses, String proguardConfig)
      throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    return new CodeInspector(
        runProguard5(programClasses, proguardConfig, proguardMap), proguardMap);
  }

  protected ProcessResult runProguard6Raw(
      Path destination,
      List<Class> programClasses,
      String proguardConfig,
      Path proguardMap,
      List<DataEntryResource> dataResources)
      throws Exception {
    return runProguard6Raw(
        destination,
        jarTestClasses(programClasses, dataResources),
        proguardConfig,
        proguardMap,
        null);
  }

  protected ProcessResult runProguard6Raw(
      Path destination, Path jar, String proguardConfig, Path proguardMap) throws Exception {
    return runProguard6Raw(destination, jar, proguardConfig, proguardMap, null);
  }

  protected ProcessResult runProguard6Raw(
      Path destination,
      Path jar,
      String proguardConfig,
      Path proguardMap,
      Consumer<ProcessResult> processResultConsumer)
      throws Exception {
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ProcessResult result =
        ToolHelper.runProguard6Raw(
            jar,
            destination,
            ToolHelper.getAndroidJar(AndroidApiLevel.N),
            proguardConfigFile,
            proguardMap);
    if (result.exitCode != 0) {
      fail("Proguard failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    if (processResultConsumer != null) {
      processResultConsumer.accept(result);
    }
    return result;
  }

  protected AndroidApp runProguard6(
      List<Class> programClasses, String proguardConfig, Path proguardMap) throws Exception {
    return runProguard6(programClasses, proguardConfig, proguardMap, null);
  }

  protected AndroidApp runProguard6(
      List<Class> programClasses,
      String proguardConfig,
      Path proguardMap,
      List<DataEntryResource> dataResources)
      throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    runProguard6Raw(proguardedJar, programClasses, proguardConfig, proguardMap, dataResources);
    return readJar(proguardedJar);
  }

  protected CodeInspector inspectProguard6Result(List<Class> programClasses, String proguardConfig)
      throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    return new CodeInspector(
        runProguard6(programClasses, proguardConfig, proguardMap), proguardMap);
  }

  protected AndroidApp runProguard6AndD8(
      List<Class> programClasses, String proguardConfig, Path proguardMap) throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ProcessResult result = ToolHelper.runProguard6Raw(
        jarTestClasses(programClasses),
        proguardedJar,
        ToolHelper.getAndroidJar(AndroidApiLevel.N),
        proguardConfigFile,
        proguardMap);
    if (result.exitCode != 0) {
      fail("Proguard failed, exit code " + result.exitCode + ", stderr:\n" + result.stderr);
    }
    return ToolHelper.runD8(readJar(proguardedJar));
  }

  protected CodeInspector inspectProguard6AndD8Result(
      List<Class> programClasses, String proguardConfig) throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    return new CodeInspector(
        runProguard6AndD8(programClasses, proguardConfig, proguardMap), proguardMap);
  }

  protected void verifyClassesPresent(
      CodeInspector codeInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = codeInspector.clazz(klass);
      assertThat(c, isPresent());
    }
  }

  protected void verifyClassesAbsent(
      CodeInspector codeInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = codeInspector.clazz(klass);
      assertThat(c, not(isPresent()));
    }
  }
}
