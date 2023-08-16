// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.transformers.ClassFileTransformer.FieldPredicate;
import com.android.tools.r8.transformers.MethodTransformer;
import java.io.IOException;
import org.junit.Test;

public class MergeFieldNameConflictTest extends HorizontalClassMergingTestBase {

  private static final String CONFLICTING_NAME = "$r8$classId";

  public MergeFieldNameConflictTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClassFileData(transform(A.class), transform(B.class))
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addHorizontallyMergedClassesInspector(
            inspector ->
                inspector.assertIsCompleteMergeGroup(A.class, B.class).assertNoOtherClassesMerged())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("foo A", "A bar 42", "B bar 2 33");
  }

  private byte[] transform(Class<?> clazz) throws IOException {
    return transformer(clazz)
        .renameField(FieldPredicate.onName("r8ClassId"), CONFLICTING_NAME)
        .addMethodTransformer(
            new MethodTransformer() {
              @Override
              public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                if (name.equals("r8ClassId")) {
                  super.visitFieldInsn(opcode, owner, CONFLICTING_NAME, descriptor);
                } else {
                  super.visitFieldInsn(opcode, owner, name, descriptor);
                }
              }
            })
        .transform();
  }

  @NeverClassInline
  public static class A {

    // Will be rewritten to $r8$classId.
    public int r8ClassId;

    private String field;

    public A(String v) {
      this.r8ClassId = 42;
      this.field = v;
    }

    @NeverInline
    public void foo() {
      System.out.println("foo " + field);
    }

    @NeverInline
    void bar() {
      System.out.println("A bar " + r8ClassId);
    }
  }

  @NeverClassInline
  public static class B {

    // Will be rewritten to $r8$classId.
    public int r8ClassId;

    private String field;

    public B(int v) {
      this.r8ClassId = 33;
      this.field = Integer.toString(v);
    }

    @NeverInline
    public void bar() {
      System.out.println("B bar " + field + " " + r8ClassId);
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
