// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.maindexlist;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DexIndexedConsumer.ArchiveConsumer;
import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.stream.Collectors;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MainDexListOutputTest extends TestBase {

  interface MyConsumer<T> {
    void accept(T element);
  }

  class TestClass {
    public void f(MyConsumer<String> s) {
      s.accept("asdf");
    }

    public void g() {
      f(System.out::println);
    }
  }

  private static String testClassMainDexName =
      "com/android/tools/r8/maindexlist/MainDexListOutputTest$TestClass.class";

  private static class TestMainDexListConsumer implements StringConsumer {
    public boolean called = false;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      called = true;
      assertTrue(string.contains(testClassMainDexName));
      assertTrue(string.contains("Lambda"));
    }
  }

  class Reporter implements DiagnosticsHandler {
    int errorCount = 0;

    @Override
    public void error(Diagnostic error) {
      errorCount++;
      assertTrue(error instanceof StringDiagnostic);
      assertTrue(error.getDiagnosticMessage().contains("main-dex"));
    }
  }

  @Rule
  public ExpectedException thrown = ExpectedException.none();

  @Test(expected = CompilationFailedException.class)
  public void testNoMainDex() throws Exception {
    Reporter reporter = new Reporter();
    try {
      Path mainDexListOutput = temp.getRoot().toPath().resolve("main-dex-output.txt");
      R8Command.builder(reporter)
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
          .addClassProgramData(
              ToolHelper.getClassAsBytes(HelloWorldMain.class), Origin.unknown())
          .setMainDexListOutputPath(mainDexListOutput)
          .build();
    } catch (CompilationFailedException e) {
      assertEquals(1, reporter.errorCount);
      throw e;
    }
  }

  @Test
  public void testWithMainDex() throws Exception {
    Path mainDexRules = writeTextToTempFile(keepMainProguardConfiguration(HelloWorldMain.class));
    Path mainDexListOutput = temp.getRoot().toPath().resolve("main-dex-output.txt");
    R8Command command =
        ToolHelper.prepareR8CommandBuilder(readClasses(HelloWorldMain.class))
            .setDisableTreeShaking(true)
            .setDisableMinification(true)
            .addMainDexRulesFiles(mainDexRules)
            .setMainDexListOutputPath(mainDexListOutput)
            .setOutput(temp.getRoot().toPath(), OutputMode.DexIndexed)
            .build();
    ToolHelper.runR8(command);
    // Main dex list with the single class.
    assertEquals(
        ImmutableList.of(HelloWorldMain.class.getTypeName().replace('.', '/') + ".class"),
        FileUtils.readAllLines(mainDexListOutput)
            .stream()
            .filter(s -> !s.isEmpty())
            .collect(Collectors.toList()));
  }

  @Test
  public void testD8DesugaredLambdasInMainDexList() throws IOException, CompilationFailedException {
    Path mainDexList = writeTextToTempFile(testClassMainDexName);
    TestMainDexListConsumer consumer = new TestMainDexListConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.K)
        .addProgramClasses(ImmutableList.of(TestClass.class, MyConsumer.class))
        .addMainDexListFiles(ImmutableList.of(mainDexList))
        .setMainDexListConsumer(consumer)
        .compile();
    assertTrue(consumer.called);
  }

  @Test
  public void testD8DesugaredLambdasInMainDexListMerging()
      throws IOException, CompilationFailedException {
    Path mainDexList = writeTextToTempFile(testClassMainDexName);
    Path dexOutput = temp.getRoot().toPath().resolve("classes.zip");
    // Build intermediate dex code first.
    testForD8()
        .setMinApi(AndroidApiLevel.K)
        .addProgramClasses(ImmutableList.of(TestClass.class, MyConsumer.class))
        .setIntermediate(true)
        .setProgramConsumer(new ArchiveConsumer(dexOutput))
        .compile();
    // Now test that when merging with a main dex list it is correctly updated.
    TestMainDexListConsumer consumer = new TestMainDexListConsumer();
    testForD8()
        .setMinApi(AndroidApiLevel.K)
        .addProgramFiles(dexOutput)
        .addMainDexListFiles(ImmutableList.of(mainDexList))
        .setMainDexListConsumer(consumer)
        .compile();
    assertTrue(consumer.called);
  }
}
