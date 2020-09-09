// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersBuilder;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptSubclassKeepsSuperTest extends TestBase {

  private static final Class<?> CLASS = TestClass.class;
  private static final String EXPECTED = StringUtils.lines("Foo!");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return TestParametersBuilder.builder().withCfRuntimes().build();
  }

  public KeptSubclassKeepsSuperTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .enableNoVerticalClassMergingAnnotations()
            .enableNeverClassInliningAnnotations()
            .addProgramClasses(CLASS, Foo.class, Bar.class)
            .addKeepMainRule(CLASS)
            .run(parameters.getRuntime(), CLASS)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep main-method rule.
    assertEquals(1, inspector.getRoots().size());
    inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    QueryNode fooClass = inspector.clazz(Reference.classFromClass(Foo.class));
    fooClass.assertPresent();

    QueryNode barClass = inspector.clazz(Reference.classFromClass(Bar.class));
    barClass.assertPresent().assertKeptBy(fooClass);
  }

  @NoVerticalClassMerging
  public abstract static class Bar {}

  @NeverClassInline
  public static final class Foo extends Bar {

    static final Foo INSTANCE = new Foo();

    public static Foo getInstance() {
      return INSTANCE;
    }

    private Foo() {}

    @NeverInline
    @NeverPropagateValue
    @Override
    public String toString() {
      return "Foo!";
    }
  }

  public static class TestClass {
    public Bar bar;

    public TestClass() {
      this.bar = Foo.getInstance();
    }

    public static void main(String[] args) {
      System.out.println(new TestClass().bar.toString());
    }
  }
}
