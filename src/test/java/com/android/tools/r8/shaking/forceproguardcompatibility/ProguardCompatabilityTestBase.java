// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
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

  protected AndroidApp runShrinkerRaw(
      Shrinker mode, List<Class> programClasses, Iterable<String> proguadConfigs) throws Exception {
    return runShrinkerRaw(
        mode, programClasses, String.join(System.lineSeparator(), proguadConfigs));
  }

  protected AndroidApp runShrinkerRaw(
      Shrinker mode, List<Class> programClasses, String proguardConfig) throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    switch (mode) {
      case PROGUARD5:
        return runProguard5Raw(programClasses, proguardConfig, proguardMap);
      case PROGUARD6:
        return runProguard6Raw(programClasses, proguardConfig, proguardMap);
      case PROGUARD6_THEN_D8:
        return runProguard6AndD8Raw(programClasses, proguardConfig, proguardMap);
      case R8_COMPAT:
        return runR8CompatRaw(programClasses, proguardConfig);
      case R8:
        return runR8Raw(programClasses, proguardConfig);
    }
    throw new IllegalArgumentException("Unknown shrinker: " + mode);
  }

  protected DexInspector runShrinker(
      Shrinker mode, List<Class> programClasses, List<String> proguadConfigs) throws Exception {
    return runShrinker(mode, programClasses, String.join(System.lineSeparator(), proguadConfigs));
  }

  protected DexInspector runShrinker(
      Shrinker mode, List<Class> programClasses, String proguardConfig) throws Exception {
    switch (mode) {
      case PROGUARD5:
        return runProguard5(programClasses, proguardConfig);
      case PROGUARD6:
        return runProguard6(programClasses, proguardConfig);
      case PROGUARD6_THEN_D8:
        return runProguard6AndD8(programClasses, proguardConfig);
      case R8_COMPAT:
        return runR8Compat(programClasses, proguardConfig);
      case R8:
        return runR8(programClasses, proguardConfig);
    }
    throw new IllegalArgumentException("Unknown shrinker: " + mode);
  }

  protected AndroidApp runR8Raw(List<Class> programClasses, String proguardConfig)
      throws Exception {
    return runR8Raw(programClasses, proguardConfig, null);
  }

  protected AndroidApp runR8Raw(
      List<Class> programClasses, String proguardConfig, Consumer<InternalOptions> configure)
      throws Exception {
    AndroidApp app = readClassesAndAndriodJar(programClasses);
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app);
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown());
    return ToolHelper.runR8(builder.build(), configure);
  }

  protected DexInspector runR8(List<Class> programClasses, String proguardConfig) throws Exception {
    return new DexInspector(runR8Raw(programClasses, proguardConfig));
  }

  protected AndroidApp runR8CompatRaw(
      List<Class> programClasses, String proguardConfig) throws Exception {
    CompatProguardCommandBuilder builder = new CompatProguardCommandBuilder(true);
    ToolHelper.allowTestProguardOptions(builder);
    builder.addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown());
    programClasses.forEach(
        clazz -> builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz)));
    builder.addLibraryFiles(ToolHelper.getAndroidJar(ToolHelper.getMinApiLevelForDexVm()));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    return ToolHelper.runR8(builder.build());
  }

  protected DexInspector runR8Compat(
      List<Class> programClasses, String proguardConfig) throws Exception {
    return new DexInspector(runR8CompatRaw(programClasses, proguardConfig));
  }

  protected DexInspector runR8CompatKeepingMain(Class mainClass, List<Class> programClasses)
      throws Exception {
    return runR8Compat(programClasses, keepMainProguardConfiguration(mainClass));
  }

  protected AndroidApp runProguard5Raw(
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

  protected DexInspector runProguard5(
      List<Class> programClasses, String proguardConfig) throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    return new DexInspector(
        runProguard5Raw(programClasses, proguardConfig, proguardMap), proguardMap);
  }

  protected AndroidApp runProguard6Raw(
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
    return readJar(proguardedJar);
  }

  protected DexInspector runProguard6(
      List<Class> programClasses, String proguardConfig) throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    return new DexInspector(
        runProguard6Raw(programClasses, proguardConfig, proguardMap), proguardMap);
  }

  protected AndroidApp runProguard6AndD8Raw(
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

  protected DexInspector runProguard6AndD8(
      List<Class> programClasses, String proguardConfig) throws Exception {
    proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    return new DexInspector(
        runProguard6AndD8Raw(programClasses, proguardConfig, proguardMap), proguardMap);
  }

  protected DexInspector runProguardKeepingMain(Class mainClass, List<Class> programClasses)
      throws Exception {
    return runProguard6AndD8(programClasses, keepMainProguardConfiguration(mainClass));
  }

  protected void verifyClassesPresent(
      DexInspector dexInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = dexInspector.clazz(klass);
      assertThat(c, isPresent());
    }
  }

  protected void verifyClassesAbsent(
      DexInspector dexInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = dexInspector.clazz(klass);
      assertThat(c, not(isPresent()));
    }
  }
}
