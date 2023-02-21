// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class GenericSignatureInvalidInfoKeepTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public GenericSignatureInvalidInfoKeepTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, Main.class)
        .addInnerClasses(A.class)
        .addProgramClassFileData(
            transformer(A.class)
                .setGenericSignature(MethodPredicate.onName("foo"), (String) null)
                .transform())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .compile()
        // This tests that no info messages are generated due to us removing the generic parameters
        // K and V from A::base.
        .assertNoInfoMessages()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("A$1::bar")
        .inspect(
            inspector -> {
              ClassSubject baseClass = inspector.clazz(Base.class);
              assertThat(baseClass, not(isPresent()));
            });
  }

  public abstract static class Base<K, V> {
    public abstract K bar(V v);
  }

  public static class A {

    public static <K, V> Base<K, V> foo() {
      return new Base<K, V>() {
        @Override
        public K bar(V f) {
          System.out.println("A$1::bar");
          return null;
        }
      };
    }
    ;
  }

  public static class Main {

    public static void main(String[] args) {
      A.foo().bar(null);
    }
  }
}
