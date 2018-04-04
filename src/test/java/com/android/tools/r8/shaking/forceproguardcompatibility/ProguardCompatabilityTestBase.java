// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.forceproguardcompatibility;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ProguardCompatabilityTestBase extends TestBase {

  public enum Shrinker {
    PROGUARD5,
    PROGUARD6,
    PROGUARD6_THEN_D8,
    R8_COMPAT,
    R8
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

  protected DexInspector runR8(List<Class> programClasses, String proguardConfig) throws Exception {
    AndroidApp app = readClasses(programClasses);
    R8Command.Builder builder = ToolHelper.prepareR8CommandBuilder(app);
    builder.addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown());
    return new DexInspector(ToolHelper.runR8(builder.build()));
  }

  protected DexInspector runR8Compat(
      List<Class> programClasses, String proguardConfig) throws Exception {
    CompatProguardCommandBuilder builder = new CompatProguardCommandBuilder(true);
    builder.addProguardConfiguration(ImmutableList.of(proguardConfig), Origin.unknown());
    programClasses.forEach(
        clazz -> builder.addProgramFiles(ToolHelper.getClassFileForTestClass(clazz)));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    return new DexInspector(ToolHelper.runR8(builder.build()));
  }

  protected DexInspector runR8CompatKeepingMain(Class mainClass, List<Class> programClasses)
      throws Exception {
    return runR8Compat(programClasses, keepMainProguardConfiguration(mainClass));
  }

  protected DexInspector runProguard5(
      List<Class> programClasses, String proguardConfig) throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    Path proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ToolHelper.runProguard(
        jarTestClasses(programClasses), proguardedJar, proguardConfigFile, proguardMap);
    return new DexInspector(readJar(proguardedJar), proguardMap);
  }

  protected DexInspector runProguard6(
      List<Class> programClasses, String proguardConfig) throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    Path proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ToolHelper.runProguard6(
        jarTestClasses(programClasses), proguardedJar, proguardConfigFile, proguardMap);
    return new DexInspector(readJar(proguardedJar), proguardMap);
  }

  protected DexInspector runProguard6AndD8(
      List<Class> programClasses, String proguardConfig) throws Exception {
    Path proguardedJar =
        File.createTempFile("proguarded", FileUtils.JAR_EXTENSION, temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    Path proguardMap = File.createTempFile("proguard", ".map", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ToolHelper.runProguard6(
        jarTestClasses(programClasses), proguardedJar, proguardConfigFile, proguardMap);
    AndroidApp app = ToolHelper.runD8(readJar(proguardedJar));
    return new DexInspector(app, proguardMap);
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
