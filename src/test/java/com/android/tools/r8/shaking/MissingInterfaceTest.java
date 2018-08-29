// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

interface GoingToBeMissed {
  void onSomeEvent(long soLong);
}

class TestClassForB112849320 {
  GoingToBeMissed instance;
  void foo(GoingToBeMissed instance) {
    System.out.println("B112849320");
    this.instance = instance;
  }

  void bar() {
    instance.onSomeEvent(8L);
  }

  public static void main(String[] args) {
    TestClassForB112849320 self = new TestClassForB112849320();
    self.foo(l -> {
      if (l > 0) {
        System.out.println(l);
      }
    });
    self.bar();
  }
}

public class MissingInterfaceTest extends TestBase {
  private static String MAIN_NAME = TestClassForB112849320.class.getCanonicalName();
  private Path libJar;
  private Path libDex;

  @Before
  public void setUp() throws Exception {
    libJar = writeToJar(ImmutableList.of(ToolHelper.getClassAsBytes(GoingToBeMissed.class)));
    libDex = temp.getRoot().toPath().resolve("lib.zip");
    AndroidApp libApp = ToolHelper.runD8(readClasses(GoingToBeMissed.class));
    libApp.writeToZip(libDex, OutputMode.DexIndexed);
  }

  @Test
  public void test_missingInterface() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-keep class " + MAIN_NAME + " {",
        "  public static void main(...);",
        "}"
    );
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClassForB112849320.class));
    builder.addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    builder.addProguardConfiguration(config, Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
    });

    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    processedApp.writeToZip(outDex, OutputMode.DexIndexed);
    ProcessResult artResult = ToolHelper.runArtRaw(
        ImmutableList.of(outDex.toString(), libDex.toString()), MAIN_NAME, null);
    assertNotEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString("B112849320"));
    assertNotEquals(-1, artResult.stderr.indexOf("AbstractMethodError"));
  }

  @Test
  public void test_passingInterfaceAsLib() throws Exception {
    List<String> config = ImmutableList.of(
        "-printmapping",
        "-keep class " + MAIN_NAME + " {",
        "  public static void main(...);",
        "}"
    );
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestClassForB112849320.class));
    builder.addLibraryFiles(ToolHelper.getDefaultAndroidJar(), libJar);
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    builder.addProguardConfiguration(config, Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
    });

    Path outDex = temp.getRoot().toPath().resolve("dex.zip");
    processedApp.writeToZip(outDex, OutputMode.DexIndexed);
    ProcessResult artResult = ToolHelper.runArtRaw(
        ImmutableList.of(outDex.toString(), libDex.toString()), MAIN_NAME, null);
    assertEquals(0, artResult.exitCode);
    assertThat(artResult.stdout, containsString("B112849320"));
    assertEquals(-1, artResult.stderr.indexOf("AbstractMethodError"));
  }

}
