// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.time.ZoneId;
import java.util.List;
import java.util.TimeZone;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicTimeConversionTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final boolean shrinkDesugaredLibrary;
  private static final String GMT = StringUtils.lines("GMT");

  @Parameters(name = "{0}, shrinkDesugaredLibrary: {1}")
  public static List<Object[]> data() {
    return buildParameters(getConversionParametersFrom(AndroidApiLevel.O), BooleanUtils.values());
  }

  public BasicTimeConversionTest(TestParameters parameters, boolean shrinkDesugaredLibrary) {
    this.shrinkDesugaredLibrary = shrinkDesugaredLibrary;
    this.parameters = parameters;
  }

  @Test
  public void testRewrittenAPICallsD8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForD8()
        .setMinApi(parameters.getApiLevel())
        .addInnerClasses(BasicTimeConversionTest.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .inspect(this::checkAPIRewritten)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(GMT);
    if (shrinkDesugaredLibrary) {
      checkKeepRules(keepRuleConsumer.get());
    }
  }

  private void checkKeepRules(String keepRules) {
    assertTrue(keepRules.contains("TimeConversion"));
  }

  @Test
  public void testRewrittenAPICallsR8() throws Exception {
    KeepRuleConsumer keepRuleConsumer = createKeepRuleConsumer(parameters);
    testForR8(parameters.getBackend())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(Executor.class)
        .addInnerClasses(BasicTimeConversionTest.class)
        .enableCoreLibraryDesugaring(parameters.getApiLevel(), keepRuleConsumer)
        .compile()
        .addDesugaredCoreLibraryRunClassPath(
            this::buildDesugaredLibrary,
            parameters.getApiLevel(),
            keepRuleConsumer.get(),
            shrinkDesugaredLibrary)
        .run(parameters.getRuntime(), Executor.class)
        .assertSuccessWithOutput(GMT);
    if (shrinkDesugaredLibrary) {
      checkKeepRules(keepRuleConsumer.get());
    }
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
                        && instr.getMethod().name.toString().equals("convert")
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
                        && instr.getMethod().name.toString().equals("convert")
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
