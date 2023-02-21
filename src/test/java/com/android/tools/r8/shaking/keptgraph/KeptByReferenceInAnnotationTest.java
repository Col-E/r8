// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByReferenceInAnnotationTest extends TestBase {

  @Parameter public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepRuntimeVisibleAnnotations()
        .enableGraphInspector()
        .setMinApi(parameters)
        .compile()
        .inspectGraph(
            inspector -> {
              QueryNode keepMainMethodRule = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

              QueryNode mainClassNode =
                  inspector
                      .clazz(Main.class)
                      .assertPresent()
                      .assertNotRenamed()
                      .assertKeptBy(keepMainMethodRule);

              // The main() method is kept by the -keep rule.
              QueryNode mainMethodNode =
                  inspector
                      .method(MethodReferenceUtils.mainMethod(Main.class))
                      .assertPresent()
                      .assertNotRenamed()
                      .assertKeptBy(keepMainMethodRule);

              // MyAnnotation is referenced from main() and is also used to annotate class Main.
              QueryNode annotationNode =
                  inspector
                      .clazz(MyAnnotation.class)
                      .assertPresent()
                      .assertRenamed()
                      .assertKeptBy(mainMethodNode);
              annotationNode.assertKeptByReferenceInAnnotationOn(annotationNode, mainClassNode);

              // ReferencedInAnnotation is referenced from inside the annotation on class Main.
              inspector
                  .clazz(ReferencedInAnnotation.class)
                  .assertPresent()
                  .assertRenamed()
                  .assertKeptByReferenceInAnnotationOn(annotationNode, mainClassNode);

              // Check the presence of an edge from main() to the annotation node.
              inspector
                  .annotation(MyAnnotation.class, mainClassNode)
                  .assertPresent()
                  .assertKeptByAnnotationOn(mainClassNode);
            })
        .run(parameters.getRuntime(), Main.class)
        .apply(
            result ->
                result.assertSuccessWithOutputLines(
                    result.inspector().clazz(ReferencedInAnnotation.class).getFinalName()));
  }

  @MyAnnotation(ReferencedInAnnotation.class)
  static class Main {

    public static void main(String[] args) {
      MyAnnotation annotation = Main.class.getAnnotation(MyAnnotation.class);
      System.out.println(annotation.value().getName());
    }
  }

  static class ReferencedInAnnotation {}

  @Retention(RetentionPolicy.RUNTIME)
  @interface MyAnnotation {

    Class<?> value();
  }
}
