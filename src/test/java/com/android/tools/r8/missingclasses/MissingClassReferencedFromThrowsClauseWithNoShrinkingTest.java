// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.missingclasses;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import org.junit.Test;

public class MissingClassReferencedFromThrowsClauseWithNoShrinkingTest
    extends MissingClassesTestBase {

  private static final String NEW_A_DESCRIPTOR = "Lfoo/a;";
  private static final String NEW_B_DESCRIPTOR = "Lfoo/b;";

  public MissingClassReferencedFromThrowsClauseWithNoShrinkingTest(TestParameters parameters) {
    super(parameters);
  }

  @Test(expected = CompilationFailedException.class)
  public void testNoShrinking() throws Exception {
    compileWithExpectedDiagnostics(
        Main.class,
        diagnostics -> {
          // TODO(b/183019116): Should not report the type as missing.
          diagnostics.assertErrorMessageThatMatches(
              containsString(
                  "Failed lookup of non-missing type: " + MissingClass.class.getTypeName()));
        },
        b -> {
          b.addProgramClassFileData(
              transformer(A.class)
                  .setClassDescriptor(NEW_A_DESCRIPTOR)
                  .removeInnerClasses()
                  .transform(),
              transformer(B.class)
                  .setClassDescriptor(NEW_B_DESCRIPTOR)
                  .removeInnerClasses()
                  .replaceClassDescriptorInMethodInstructions(descriptor(A.class), NEW_A_DESCRIPTOR)
                  .transform());
          b.enableInliningAnnotations();
          b.addKeepClassAndMembersRules(DescriptorUtils.descriptorToJavaType(NEW_A_DESCRIPTOR));
          b.noTreeShaking();
        });
  }

  public static class Main {

    public static void main(String[] args) {}
  }

  public static class /* will be: foo.A */ A {

    @NeverInline
    public static void foo() throws MissingClass {
      System.out.println("Hello World");
    }
  }

  public static class /* will be: foo.B */ B {

    public static void callFoo() {
      A.foo();
    }
  }
}
