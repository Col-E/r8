// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/289361079. */
@RunWith(Parameterized.class)
public class ClInitMergeSuperTypeApiLevelTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        // Emulate a standard AGP setup where we compile with a new android jar on boot classpath.
        .addLibraryFiles(ToolHelper.getMostRecentAndroidJar())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepClassAndMembersRules(Main.class)
        .addHorizontallyMergedClassesInspector(
            inspector -> inspector.assertClassesMerged(A.class, B.class))
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(A.class);
              assertThat(clazz, isPresent());
              MethodSubject init = clazz.uniqueInstanceInitializer();
              assertThat(init, isPresent());
              TypeReference executableTypeRef =
                  Reference.typeFromTypeName(typeName(Executable.class));
              // TODO(b/289361079): This should not be of type Executable since this was introduced
              //  at api 26.
              assertEquals(executableTypeRef, init.getParameter(0).getTypeReference());
              // TODO(b/289361079): This should not be of type Executable since this was introduced
              //  at api 26.
              assertTrue(
                  clazz.allFields().stream()
                      .anyMatch(
                          f -> executableTypeRef.equals(f.getFinalReference().getFieldType())));
            })
        .run(parameters.getRuntime(), Main.class)
        // The test succeeds for some unknown reason.
        .assertSuccessWithOutputLines(typeName(Main.class));
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      if (System.currentTimeMillis() > 0) {
        System.out.println(
            new A(Main.class.getDeclaredConstructor()).newInstance().getClass().getName());
      } else {
        System.out.println(
            new B(Main.class.getDeclaredMethod("main", String[].class))
                .newInstance()
                .getClass()
                .getName());
      }
    }
  }

  public abstract static class Factory {

    abstract Object newInstance() throws Exception;
  }

  public static class A extends Factory {

    public final Constructor<?> constructor;

    public A(Constructor<?> constructor) {
      this.constructor = constructor;
    }

    @Override
    @NeverInline
    Object newInstance() throws Exception {
      return constructor.newInstance();
    }
  }

  public static class B extends Factory {

    public final Method method;

    public B(Method method) {
      this.method = method;
    }

    @Override
    @NeverInline
    Object newInstance() throws Exception {
      return method.invoke(null);
    }
  }
}
