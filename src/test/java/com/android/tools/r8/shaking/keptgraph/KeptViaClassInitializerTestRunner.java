// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.function.Supplier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptViaClassInitializerTestRunner extends TestBase {

  @NeverClassInline
  public static class A {

    @Override
    public String toString() {
      return "I'm an A";
    }
  }

  @NeverClassInline
  public enum T {
    A(A::new);

    @NeverPropagateValue private final Supplier<Object> factory;

    T(Supplier<Object> factory) {
      this.factory = factory;
    }

    public Object create() {
      return factory.get();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      System.out.println(T.A.create());
    }
  }

  private static final String CLASS_NAME = KeptViaClassInitializerTestRunner.class.getTypeName();
  private static final String EXPECTED = StringUtils.lines("I'm an A");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        .withDexRuntimesStartingFromIncluding(Version.V7_0_0)
        .withAllApiLevels()
        .build();
  }

  public KeptViaClassInitializerTestRunner(TestParameters parameters) {
    this.parameters = parameters;
  }

  public static String KEPT_REASON_SUFFIX =
      StringUtils.lines(
          // The full reason is not shared between CF and DEX due to desugaring.
          "|  void " + CLASS_NAME + "$T.<clinit>()",
          "|- is reachable from:",
          "|  com.android.tools.r8.shaking.keptgraph.KeptViaClassInitializerTestRunner$T",
          "|- is referenced from:",
          "|  void " + CLASS_NAME + "$Main.main(java.lang.String[])",
          "|- is referenced in keep rule:",
          "|  -keep class " + CLASS_NAME + "$Main { void main(java.lang.String[]); }");

  @Test
  public void testKeptMethod() throws Exception {
    assumeTrue(ToolHelper.getDexVm().getVersion().isAtLeast(Version.V7_0_0));

    MethodReference mainMethod =
        methodFromMethod(Main.class.getDeclaredMethod("main", String[].class));

    WhyAreYouKeepingConsumer consumer = new WhyAreYouKeepingConsumer(null);
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector(consumer)
            .addProgramClassesAndInnerClasses(Main.class, A.class, T.class)
            .addKeepMethodRules(mainMethod)
            .enableMemberValuePropagationAnnotations()
            .setMinApi(AndroidApiLevel.N)
            .apply(
                b -> {
                  if (parameters.isDexRuntime()) {
                    b.addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.N));
                  }
                })
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutput(EXPECTED)
            .graphInspector();

    // The only root should be the keep main-method rule.
    assertEquals(1, inspector.getRoots().size());
    QueryNode root = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    consumer.printWhyAreYouKeeping(classFromClass(A.class), new PrintStream(baos));
    assertThat(baos.toString(), containsString(KEPT_REASON_SUFFIX));

    // TODO(b/124499108): Currently synthetic lambda classes are referenced,
    //  should be their originating context.
    if (parameters.isDexRuntime()) {
      assertThat(baos.toString(), containsString("-$$Lambda$"));
    } else {
      assertThat(baos.toString(), not(containsString("-$$Lambda$")));
    }
  }
}
