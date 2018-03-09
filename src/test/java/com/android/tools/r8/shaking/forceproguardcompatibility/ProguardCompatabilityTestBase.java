// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

public class ProguardCompatabilityTestBase extends TestBase {

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

  protected DexInspector runProguard(
      List<Class> programClasses, String proguardConfig) throws Exception {
    Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ToolHelper.runProguard(jarTestClasses(programClasses), proguardedJar, proguardConfigFile, null);
    return new DexInspector(readJar(proguardedJar));
  }

  protected DexInspector runProguardAndD8(
      List<Class> programClasses, String proguardConfig) throws Exception {
    Path proguardedJar = File.createTempFile("proguarded", ".jar", temp.getRoot()).toPath();
    Path proguardConfigFile = File.createTempFile("proguard", ".config", temp.getRoot()).toPath();
    FileUtils.writeTextFile(proguardConfigFile, proguardConfig);
    ToolHelper.runProguard(jarTestClasses(programClasses), proguardedJar, proguardConfigFile, null);
    AndroidApp app = ToolHelper.runD8(readJar(proguardedJar));
    return new DexInspector(app);
  }

  protected DexInspector runProguardKeepingMain(Class mainClass, List<Class> programClasses)
      throws Exception {
    return runProguardAndD8(programClasses, keepMainProguardConfiguration(mainClass));
  }
}
