// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceTest extends TestBase {
  private Backend backend;
  private CompilationMode mode;

  @Parameters(name = "Backend: {0}, mode: {1}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (Backend backend : Backend.values()) {
      for (CompilationMode mode : CompilationMode.values()) {
        parameters.add(new Object[] {backend, mode});
      }
    }
    return parameters;
  }

  public RetraceTest(Backend backend, CompilationMode mode) {
    this.backend = backend;
    this.mode = mode;
  }

  private List<String> retrace(String map, List<String> stackTrace) throws IOException {
    Path t = temp.newFolder().toPath();
    Path mapFile = t.resolve("map");
    Path stackTraceFile = t.resolve("stackTrace");
    FileUtils.writeTextFile(mapFile, map);
    FileUtils.writeTextFile(stackTraceFile, stackTrace);
    return StringUtils.splitLines(ToolHelper.runRetrace(mapFile, stackTraceFile));
  }

  private List<String> extractStackTrace(ProcessResult result) {
    ImmutableList.Builder<String> builder = ImmutableList.builder();
    List<String> stderr = StringUtils.splitLines(result.stderr);
    Iterator<String> iterator = stderr.iterator();
    while (iterator.hasNext()) {
      String line = iterator.next();
      if (line.startsWith("Exception in thread \"main\"")) {
        break;
      }
    }
    iterator.forEachRemaining(builder::add);
    return builder.build();
  }

  public void runTest(Class<?> mainClass, BiConsumer<List<String>, List<String>> checker)
      throws Exception {
    StringBuilder proguardMapBuilder = new StringBuilder();
    AndroidApp output =
        ToolHelper.runR8(
            R8Command.builder()
                .setMode(mode)
                .addClassProgramData(ToolHelper.getClassAsBytes(mainClass), Origin.unknown())
                .addProguardConfiguration(
                    ImmutableList.of(keepMainProguardConfiguration(mainClass)), Origin.unknown())
                .setProgramConsumer(emptyConsumer(backend))
                .setProguardMapConsumer((string, ignore) -> proguardMapBuilder.append(string))
                .build());

    ProcessResult result = runOnVMRaw(output, mainClass, backend);
    List<String> stackTrace = extractStackTrace(result);
    List<String> retracesStackTrace = retrace(proguardMapBuilder.toString(), stackTrace);
    checker.accept(stackTrace, retracesStackTrace);
  }

  @Test
  public void test() throws Exception {
    runTest(
        Main.class,
        (List<String> stackTrace, List<String> retracesStackTrace) -> {
          assertEquals(
              mode == CompilationMode.RELEASE, stackTrace.size() != retracesStackTrace.size());
          if (mode == CompilationMode.DEBUG) {
            assertThat(stackTrace.get(0), not(containsString("method2")));
            assertThat(stackTrace.get(1), not(containsString("method1")));
            assertThat(stackTrace.get(2), containsString("main"));
          }
          assertEquals(3, retracesStackTrace.size());
          assertThat(retracesStackTrace.get(0), containsString("method2"));
          assertThat(retracesStackTrace.get(1), containsString("method1"));
          assertThat(retracesStackTrace.get(2), containsString("main"));
        });
  }
}

class Main {
  public static void method2(int i) {
    System.out.println("In method2");
    throw null;
  }

  public static void method1(String s) {
    System.out.println("In method1");
    for (int i = 0; i < 10; i++) {
      method2(Integer.parseInt(s));
    }
  }

  public static void main(String[] args) {
    System.out.println("In main");
    method1("1");
  }
}
