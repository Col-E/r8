// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class LambdaEnumUnboxingEmptyEnumObjectTest extends EnumUnboxingTestBase {

  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters(getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public LambdaEnumUnboxingEmptyEnumObjectTest(
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
        .addKeepRules(enumKeepRules.getKeepRules())
        .enableNeverClassInliningAnnotations()
        .addDontObfuscate()
        .enableNoVerticalClassMergingAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .addEnumUnboxingInspector(
            inspector ->
                inspector.assertUnboxedIf(
                    enumKeepRules.isNone() && parameters.getBackend().isDex(), MyEnum.class))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("null");
  }

  @NeverClassInline
  enum MyEnum {}

  @NoVerticalClassMerging
  interface ObjectConsumer<T> {

    void accept(T o);
  }

  static class Main {

    public static void main(String[] args) {
      executeObject(e -> System.out.println(String.valueOf(e)));
    }

    @NeverInline
    static void executeObject(ObjectConsumer<MyEnum> consumer) {
      consumer.accept(null);
    }
  }
}
