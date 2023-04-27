// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicEnumMergingKeepSubtypeTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  private static final String SUBTYPE_NAME = EnumWithVirtualOverride.class.getTypeName() + "$1";

  public BasicEnumMergingKeepSubtypeTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-keep class " + SUBTYPE_NAME + " { public void method(); }")
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> opt.testing.enableEnumWithSubtypesUnboxing = true)
        .addEnumUnboxingInspector(
            inspector -> inspector.assertNotUnboxed(EnumWithVirtualOverride.class))
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .inspect(this::methodKept)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "B");
  }

  private void methodKept(CodeInspector inspector) {
    assertTrue(inspector.clazz(SUBTYPE_NAME).uniqueMethodWithFinalName("method").isPresent());
  }

  enum EnumWithVirtualOverride {
    A {
      public void method() {
        System.out.println("a");
      }
    },
    B;

    public void method() {
      System.out.println(name());
    }
  }

  static class Main {

    public static void main(String[] args) {
      EnumWithVirtualOverride.A.method();
      EnumWithVirtualOverride.B.method();
    }
  }
}
