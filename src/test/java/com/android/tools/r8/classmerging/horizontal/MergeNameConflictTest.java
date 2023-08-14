// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.IOException;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MergeNameConflictTest extends TestBase {

  private static final String CONFLICTING_NAME =
      "bar$com$android$tools$r8$classmerging$horizontal$MergeNameConflictTest$A";

  protected final TestParameters parameters;
  private final boolean toPrivate;

  public MergeNameConflictTest(TestParameters parameters, boolean toPrivate) {
    this.parameters = parameters;
    this.toPrivate = toPrivate;
  }

  @Parameters(name = "{0}, pvt: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(transformedA(toPrivate))
        .addProgramClasses(B.class, Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo A", "A bar A", "ConflictingName", "B bar 2");
  }

  private byte[] transformedA(boolean toPrivate) throws IOException {
    return transformer(A.class)
        .setAccessFlags(
            MethodPredicate.onName("toRename"),
            f -> {
              if (toPrivate) {
                f.setPrivate();
              }
            })
        .renameMethod(MethodPredicate.onName("toRename"), CONFLICTING_NAME)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitMethodInsn(
                  final int opcode,
                  final String owner,
                  final String name,
                  final String descriptor,
                  final boolean isInterface) {
                if (name.equals("toRename")) {
                  int newOpcode = toPrivate ? INVOKESPECIAL : opcode;
                  super.visitMethodInsn(
                      newOpcode, owner, CONFLICTING_NAME, descriptor, isInterface);
                } else {
                  super.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                }
              }
            })
        .transform();
  }

  @NeverClassInline
  public static class A {

    private String field;

    public A(String v) {
      this.field = v;
    }

    @NeverInline
    public void foo() {
      System.out.println("foo " + field);
    }

    @NeverInline
    void bar() {
      System.out.println("A bar " + field);
      toRename();
    }

    // Will be renamed to bar$com$android$tools$r8$classmerging$horizontal$MergeNameConflictTest$A.
    @NeverInline
    void toRename() {
      System.out.println("ConflictingName");
    }
  }

  @NeverClassInline
  public static class B {

    private String field;

    public B(int v) {
      this.field = Integer.toString(v);
    }

    @NeverInline
    public void bar() {
      System.out.println("B bar " + field);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A a = new A("A");
      a.foo();
      a.bar();
      B b = new B(2);
      b.bar();
    }
  }
}
