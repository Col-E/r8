// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.arrays;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This is a reproduction of b/283715197. It actually does not reproduce on our headless VMs, so we
 * cannot assert an error - only the existences of the instruction that causes verification error.
 */
@RunWith(Parameterized.class)
public class FilledArrayDataRemoveCheckCastTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(Main.class)
        .addKeepClassAndMembersRules(Base.class)
        .addKeepClassRulesWithAllowObfuscation(Sub1.class, Sub2.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Hello", "World", "Hello", "World")
        .inspect(
            inspector -> {
              ClassSubject mainClass = inspector.clazz(Main.class);
              assertThat(mainClass, isPresent());
              MethodSubject iterateSubClasses =
                  mainClass.uniqueMethodWithOriginalName("iterateSubClasses");
              assertThat(iterateSubClasses, isPresent());
              Optional<InstructionSubject> filledNewArrayInIterateSubClasses =
                  iterateSubClasses
                      .streamInstructions()
                      .filter(InstructionSubject::isFilledNewArray)
                      .findFirst();
              MethodSubject iterateBaseClasses =
                  mainClass.uniqueMethodWithOriginalName("iterateBaseClasses");
              assertThat(iterateBaseClasses, isPresent());
              Optional<InstructionSubject> filledNewArrayInIterateBaseClasses =
                  iterateBaseClasses
                      .streamInstructions()
                      .filter(InstructionSubject::isFilledNewArray)
                      .findFirst();
              assertEquals(
                  parameters.canUseFilledNewArrayOnNonStringObjects(),
                  filledNewArrayInIterateBaseClasses.isPresent());
              assertEquals(
                  parameters.canUseFilledNewArrayOnNonStringObjects()
                      && parameters.canUseSubTypesInFilledNewArray(),
                  filledNewArrayInIterateSubClasses.isPresent());
            });
  }

  public abstract static class Base {

    public abstract String toString();
  }

  public static class Sub1 extends Base {

    @Override
    public String toString() {
      return "Hello";
    }
  }

  public static class Sub2 extends Base {

    @Override
    public String toString() {
      return "World";
    }
  }

  public static class Main {

    public static void main(String[] args) {
      iterateBaseClasses(new Sub1(), new Sub2());
      iterateSubClasses();
    }

    public static void iterateBaseClasses(Base b1, Base b2) {
      Base[] arr = new Base[] {b1, b2};
      iterate(arr);
    }

    public static void iterateSubClasses() {
      Base[] arr = new Base[] {new Sub1(), new Sub2()};
      iterate(arr);
    }

    public static void iterate(Object[] arr) {
      for (Object b : arr) {
        System.out.println(b.toString());
      }
    }
  }
}
