// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.KeepUnusedArguments;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * Similar to {@link HorizontalClassMergingAfterConstructorShrinkingTest}, but extended so that
 * {@code B.<init>(Parent)} needs to be correctly lens rewritten in the horizontal class merger,
 * since {@link Parent} is subject to repackaging, which runs prior to horizontal class merging.
 */
@RunWith(Parameterized.class)
public class HorizontalClassMergingAfterConstructorShrinkingWithRepackagingTest extends TestBase {

  @Parameter(0)
  public boolean enableRetargetingOfConstructorBridgeCalls;

  @Parameter(1)
  public TestParameters parameters;

  @Parameters(name = "{1}, retarget: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        getTestParameters()
            .withDexRuntimes()
            .withApiLevelsStartingAtIncluding(AndroidApiLevel.L)
            .build());
  }

  @Test
  public void test() throws Exception {
    assertTrue(parameters.canHaveNonReboundConstructorInvoke());
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRules("-repackageclasses")
        .addOptionsModification(
            options ->
                options
                    .getRedundantBridgeRemovalOptions()
                    .setEnableRetargetingOfConstructorBridgeCalls(
                        enableRetargetingOfConstructorBridgeCalls))
        .addOptionsModification(
            options -> options.horizontalClassMergerOptions().disableInitialRoundOfClassMerging())
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableUnusedArgumentAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              // Verify Parent and Parent.<init>(Parent) are present and that Parent has been
              // repackaged
              // into the default package.
              ClassSubject parentClassSubject = inspector.clazz(Parent.class);
              assertThat(parentClassSubject, isPresent());
              assertEquals("", parentClassSubject.getDexProgramClass().getType().getPackageName());

              MethodSubject parentInstanceInitializerSubject =
                  parentClassSubject.uniqueInstanceInitializer();
              assertThat(parentInstanceInitializerSubject, isPresent());
              assertEquals(
                  parentClassSubject.asTypeSubject(),
                  parentInstanceInitializerSubject.getParameter(0));

              // Verify that A and A.<init>(Parent) are present.
              ClassSubject aClassSubject = inspector.clazz(A.class);
              assertThat(aClassSubject, isPresent());
              assertEquals("", aClassSubject.getDexProgramClass().getType().getPackageName());

              MethodSubject aInstanceInitializerSubject = aClassSubject.uniqueInstanceInitializer();
              assertThat(aInstanceInitializerSubject, isPresent());
              assertEquals(
                  parentClassSubject.asTypeSubject(), aInstanceInitializerSubject.getParameter(0));

              // Verify that B's initializer was removed.
              ClassSubject bClassSubject = inspector.clazz(B.class);
              assertThat(bClassSubject, isPresent());
              assertEquals("", bClassSubject.getDexProgramClass().getType().getPackageName());
              assertEquals(
                  0, bClassSubject.allMethods(FoundMethodSubject::isInstanceInitializer).size());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("B");
  }

  public static class Main {

    static {
      new B(null).setFieldOnB().printFieldOnB();
    }

    public static void main(String[] args) {
      if (System.currentTimeMillis() < 0) {
        new A(null).setFieldOnA().printFieldOnA();
      }
    }
  }

  public static class Parent {

    @KeepConstantArguments
    @KeepUnusedArguments
    public Parent(Parent parent) {}
  }

  @NeverClassInline
  public static class A extends Parent {

    Object field;

    @KeepConstantArguments
    @NeverInline
    public A(Parent parent) {
      super(parent);
      System.out.println("Ouch!");
    }

    @NeverInline
    public A setFieldOnA() {
      field = "A";
      return this;
    }

    @NeverInline
    public void printFieldOnA() {
      System.out.println(field);
    }
  }

  @NeverClassInline
  public static class B extends Parent {

    Object field;

    // Removed by constructor shrinking.
    @KeepConstantArguments
    @NeverInline
    public B(Parent parent) {
      super(parent);
    }

    @NeverInline
    public B setFieldOnB() {
      field = "B";
      return this;
    }

    @NeverInline
    public void printFieldOnB() {
      System.out.println(field);
    }
  }
}
