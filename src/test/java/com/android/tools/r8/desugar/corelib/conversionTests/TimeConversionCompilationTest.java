// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.corelib.conversionTests;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.Test;

public class TimeConversionCompilationTest extends APIConversionTestBase {

  @Test
  public void testTimeGeneratedDex() throws Exception {
    testForD8()
        .addProgramFiles(getTimeConversionClasses())
        .compile()
        .inspect(this::checkTimeConversionGeneratedDex);
  }

  private void checkTimeConversionGeneratedDex(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz("j$.time.TimeConversions");
    assertThat(clazz, isPresent());
    assertEquals(9, clazz.allMethods().size());
  }

  @Test
  public void testRewrittenAPICalls() throws Exception {
    testForD8()
        .setMinApi(AndroidApiLevel.B)
        .addInnerClasses(TimeConversionCompilationTest.class)
        .enableCoreLibraryDesugaring(AndroidApiLevel.B)
        .compile()
        .inspect(this::checkAPIRewritten)
        .addDesugaredCoreLibraryRunClassPath(this::buildDesugaredLibrary, AndroidApiLevel.B)
        .run(new DexRuntime(DexVm.ART_9_0_0_HOST), Executor.class);
  }

  private void checkAPIRewritten(CodeInspector inspector) {
    MethodSubject mainMethod = inspector.clazz(Executor.class).uniqueMethodWithName("main");
    // Check the API calls are not using j$ types.
    assertTrue(
        mainMethod
            .streamInstructions()
            .anyMatch(
                instr ->
                    instr.isInvokeStatic()
                        && instr.getMethod().name.toString().equals("getTimeZone")
                        && instr
                            .getMethod()
                            .proto
                            .parameters
                            .values[0]
                            .toString()
                            .equals("java.time.ZoneId")));
    assertTrue(
        mainMethod
            .streamInstructions()
            .anyMatch(
                instr ->
                    instr.isInvokeVirtual()
                        && instr.getMethod().name.toString().equals("toZoneId")
                        && instr
                            .getMethod()
                            .proto
                            .returnType
                            .toString()
                            .equals("java.time.ZoneId")));
    // Check the conversion instructions are present.
    assertTrue(
        mainMethod
            .streamInstructions()
            .anyMatch(
                instr ->
                    instr.isInvokeStatic()
                        && instr.getMethod().name.toString().equals("to")
                        && instr
                            .getMethod()
                            .proto
                            .parameters
                            .values[0]
                            .toString()
                            .equals("j$.time.ZoneId")));
    assertTrue(
        mainMethod
            .streamInstructions()
            .anyMatch(
                instr ->
                    instr.isInvokeStatic()
                        && instr.getMethod().name.toString().equals("from")
                        && instr
                            .getMethod()
                            .proto
                            .parameters
                            .values[0]
                            .toString()
                            .equals("java.time.ZoneId")));
  }

  static class Executor {

    public static void main(String[] args) {
      ZoneId zoneId = ZoneId.systemDefault();
      // Following is a call where java.time.ZoneId is a parameter type (getTimeZone()).
      TimeZone timeZone = TimeZone.getTimeZone(zoneId);
      // Following is a call where java.time.ZoneId is a return type (toZoneId()).
      System.out.println(timeZone.toZoneId().getId());
    }
  }
}
