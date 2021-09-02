// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassOrMemberSubject;
import java.util.ArrayList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugarLambdaDontPublicize extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private final TestParameters parameters;

  public DesugarLambdaDontPublicize(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDesugar() throws Exception {
    testForDesugaring(parameters)
        .addInnerClasses(DesugarLambdaDontPublicize.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("foobar")
        .inspectIf(
            DesugarTestConfiguration::isDesugared,
            inspector -> {
              // Ensure that we don't add an extra public method for the lambda call.
              // Mockito will potentially mock this, see: b/195402351
              long count =
                  inspector.clazz(TestClass.class).allMethods().stream()
                      .filter(ClassOrMemberSubject::isPublic)
                      .count();
              assertEquals(count, 3);
            });
  }

  public static class StringList extends ArrayList<String> {
    public void forEachString(MyConsumer<String> consumer) {
      for (String s : this) {
        consumer.accept(s);
      }
    }
  }

  public interface MyConsumer<T> {
    void accept(T s);
  }

  public static class TestClass {

    public void test() {
      StringList list = new StringList();

      list.add("foobar");
      list.forEachString(
          s -> {
            class MyConsumerImpl implements MyConsumer<String> {
              public void accept(String s) {
                System.out.println(s);
              }
            }
            new MyConsumerImpl().accept(s);
          });
    }

    public static void main(String[] args) {
      new TestClass().test();
    }
  }
}
