// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing.enummerging;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.enumunboxing.EnumUnboxingTestBase;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class BasicEnumMergingTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public BasicEnumMergingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(BasicEnumMergingTest.class)
        .addKeepMainRule(Main.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .addOptionsModification(opt -> opt.testing.enableEnumWithSubtypesUnboxing = true)
        .addEnumUnboxingInspector(
            inspector ->
                inspector
                    .assertUnboxed(
                        EnumWithVirtualOverride.class,
                        EnumWithVirtualOverrideAndPrivateMethod.class,
                        EnumWithVirtualOverrideWide.class)
                    .assertNotUnboxed(EnumWithVirtualOverrideAndPrivateField.class))
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addOptionsModification(opt -> opt.testing.enableEnumUnboxingDebugLogs = true)
        .setMinApi(parameters)
        .allowDiagnosticInfoMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("a", "B", "a", "B", "a", "B", "A 1 1.0 A", "B");
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

  enum EnumWithVirtualOverrideAndPrivateMethod {
    A {
      @NeverInline
      private void privateMethod() {
        System.out.println("a");
      }

      public void methodpm() {
        privateMethod();
      }
    },
    B;

    public void methodpm() {
      System.out.println(name());
    }
  }

  enum EnumWithVirtualOverrideWide {
    A {
      public void methodpmw(long l1, double d2, EnumWithVirtualOverrideWide itself) {
        System.out.println("A " + l1 + " " + d2 + " " + itself);
      }
    },
    B;

    public void methodpmw(long l1, double d2, EnumWithVirtualOverrideWide itself) {
      System.out.println(name());
    }
  }

  enum EnumWithVirtualOverrideAndPrivateField {
    A {
      private String a = "a";

      public void methodpf() {
        System.out.println(a);
      }
    },
    B;

    public void methodpf() {
      System.out.println(name());
    }
  }

  static class Main {

    public static void main(String[] args) {
      EnumWithVirtualOverrideAndPrivateMethod.A.methodpm();
      EnumWithVirtualOverrideAndPrivateMethod.B.methodpm();
      EnumWithVirtualOverrideAndPrivateField.A.methodpf();
      EnumWithVirtualOverrideAndPrivateField.B.methodpf();
      EnumWithVirtualOverride.A.method();
      EnumWithVirtualOverride.B.method();
      EnumWithVirtualOverrideWide.A.methodpmw(1L, 1.0, EnumWithVirtualOverrideWide.A);
      EnumWithVirtualOverrideWide.B.methodpmw(1L, 1.0, EnumWithVirtualOverrideWide.A);
    }
  }
}
