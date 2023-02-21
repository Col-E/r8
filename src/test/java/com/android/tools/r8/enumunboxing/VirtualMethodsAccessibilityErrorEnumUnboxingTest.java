// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.enumunboxing;

import static org.hamcrest.CoreMatchers.containsString;
import static org.objectweb.asm.Opcodes.ACC_PRIVATE;
import static org.objectweb.asm.Opcodes.ACC_STATIC;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.ClassTransformer;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class VirtualMethodsAccessibilityErrorEnumUnboxingTest extends EnumUnboxingTestBase {
  private final TestParameters parameters;
  private final boolean enumValueOptimization;
  private final EnumKeepRules enumKeepRules;

  @Parameters(name = "{0} valueOpt: {1} keep: {2}")
  public static List<Object[]> data() {
    return enumUnboxingTestParameters();
  }

  public VirtualMethodsAccessibilityErrorEnumUnboxingTest(
      TestParameters parameters, boolean enumValueOptimization, EnumKeepRules enumKeepRules) {
    this.parameters = parameters;
    this.enumValueOptimization = enumValueOptimization;
    this.enumKeepRules = enumKeepRules;
  }

  @Test
  public void testEnumUnboxing() throws Exception {
    byte[] testClassData =
        transformer(TestClass.class)
            .addClassTransformer(
                new ClassTransformer() {
                  @Override
                  public MethodVisitor visitMethod(
                      int access,
                      String name,
                      String descriptor,
                      String signature,
                      String[] exceptions) {
                    if (name.equals("privateMethod")) {
                      return super.visitMethod(
                          ACC_STATIC | ACC_PRIVATE, name, descriptor, signature, exceptions);
                    } else {
                      return super.visitMethod(access, name, descriptor, signature, exceptions);
                    }
                  }
                })
            .transform();
    testForR8(parameters.getBackend())
        .addDontObfuscate()
        .addProgramClasses(MyEnum.class)
        .addProgramClassFileData(testClassData)
        .addKeepMainRule(TestClass.class)
        .addKeepRules(enumKeepRules.getKeepRules())
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addOptionsModification(opt -> enableEnumOptions(opt, enumValueOptimization))
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(containsString("java.lang.IllegalAccessError"));
  }

  public static class TestClass {
    // The method privateMethod will be transformed into a private method.
    @NeverInline
    protected static int privateMethod() {
      return System.currentTimeMillis() > 0 ? 4 : 3;
    }

    public static void main(String[] args) {
      System.out.println(MyEnum.A.ordinal());
      MyEnum.print();
    }
  }

  @NeverClassInline
  enum MyEnum {
    A,
    B;

    @NeverInline
    static void print() {
      // This should raise an accessibility error, weither the enum is unboxed or not.
      System.out.println(TestClass.privateMethod());
    }
  }
}
