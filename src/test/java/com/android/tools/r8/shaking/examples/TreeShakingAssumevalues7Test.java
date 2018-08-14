// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.examples;

import com.android.tools.r8.TestBase.MinifyMode;
import com.android.tools.r8.shaking.TreeShakingTest;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.ConstStringInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class TreeShakingAssumevalues7Test extends TreeShakingTest {

  @Parameters(name = "mode:{0}-{1} minify:{2}")
  public static Collection<Object[]> data() {
    List<Object[]> parameters = new ArrayList<>();
    for (MinifyMode minify : MinifyMode.values()) {
      parameters.add(new Object[] {Frontend.JAR, Backend.CF, minify});
      parameters.add(new Object[] {Frontend.JAR, Backend.DEX, minify});
      parameters.add(new Object[] {Frontend.DEX, Backend.DEX, minify});
    }
    return parameters;
  }

  public TreeShakingAssumevalues7Test(Frontend frontend, Backend backend, MinifyMode minify) {
    super("examples/assumevalues7", "assumevalues7.Assumevalues", frontend, backend, minify);
  }

  @Test
  public void test() throws Exception {
    runTest(
        getBackend() == Backend.DEX ? TreeShakingAssumevalues7Test::assumevalues7CheckCode : null,
        TreeShakingAssumevalues7Test::assumevalues7CheckOutput,
        null,
        ImmutableList.of("src/test/examples/assumevalues7/keep-rules.txt"));
  }

  private static void assumevalues7CheckCode(CodeInspector inspector) {
    inspector.forAllClasses(c -> {
      c.forAllMethods(m -> {
        if (m.getFinalName().equals("main")) {
          m.iterateInstructions().forEachRemaining(i -> {
            if (i.isConstString(JumboStringMode.ALLOW)) {
              ConstStringInstructionSubject str = (ConstStringInstructionSubject) i;
              assert !str.getString().toASCIIString().contains("NOPE");
            }
          });
        }
      });
    });
  }

  private static void assumevalues7CheckOutput(String output1, String output2) {
    Assert.assertEquals(StringUtils.lines("NOPE_STATIC_NOT_NULL", "NOPE_NOT_NULL", "OK"), output1);
    Assert.assertEquals(StringUtils.lines("OK"), output2);
  }
}