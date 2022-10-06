// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import static org.junit.Assert.assertThrows;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression for b/247146910. */
@RunWith(Parameterized.class)
public class EnumImplementingInterfaceTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  private final String[] EXPECTED = new String[] {"Foo", "Hello World!"};

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public EnumImplementingInterfaceTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(EnumImplementingInterfaceTest.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    // TODO(b/247146910): We should not fail compilation. If running without assertions this
    //  inserts throw null for the code.
    assertThrows(
        CompilationFailedException.class,
        () ->
            testForR8(parameters.getBackend())
                .addInnerClasses(EnumImplementingInterfaceTest.class)
                .addKeepMainRule(Main.class)
                .addKeepRules(enumKeepRules.getKeepRules())
                .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
                .compile());
  }

  public interface I {

    String get();
  }

  @NeverClassInline
  public enum OtherEnum implements I {
    C("Foo"),
    D("Bar");

    private final String value;

    OtherEnum(String value) {
      this.value = value;
    }

    @Override
    public String get() {
      return value;
    }
  }

  @NeverClassInline
  public enum MyEnum {
    A(OtherEnum.C),
    B(OtherEnum.D);

    public I otherEnum;

    MyEnum(I otherEnum) {
      this.otherEnum = otherEnum;
      if (System.currentTimeMillis() == 0) {
        this.otherEnum = null;
      }
    }
  }

  public static class Main implements I {

    public static void main(String[] args) throws Exception {
      System.out.println(MyEnum.A.otherEnum.get());
      System.out.println(new Main().get());
    }

    @Override
    public String get() {
      if (System.currentTimeMillis() == 0) {
        throw new RuntimeException("Foo");
      }
      return "Hello World!";
    }
  }
}
