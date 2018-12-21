// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.CollectingGraphConsumer;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

class Main {

  public static void foo() {
    bar();
  }

  @NeverInline
  public static void bar() {
    System.out.println("called bar");
  }

  @NeverInline
  public static void baz() {
    System.out.println("called baz");
  }
}

public class KeptMethodTest extends TestBase {

  final Backend backend = Backend.DEX;

  @Test
  public void testKeptMethod()
      throws NoSuchMethodException, CompilationFailedException, IOException, ExecutionException {
    MethodReference fooMethod = Reference.methodFromMethod(Main.class.getMethod("foo"));
    MethodReference barMethod = Reference.methodFromMethod(Main.class.getMethod("bar"));
    MethodReference bazMethod = Reference.methodFromMethod(Main.class.getMethod("baz"));

    CollectingGraphConsumer graphConsumer = new CollectingGraphConsumer(null);
    R8TestBuilder builder =
        testForR8(backend)
            .setKeptGraphConsumer(graphConsumer)
            .enableInliningAnnotations()
            .addProgramClasses(Main.class)
            .addKeepMethodRules(fooMethod);

    CodeInspector codeInspector = builder.compile().inspector();
    GraphInspector graphInspector = new GraphInspector(graphConsumer, codeInspector);

    // The only root should be the method rule.
    assertEquals(1, graphInspector.getRoots().size());
    assertTrue(graphInspector.isPresent(fooMethod));
    assertTrue(graphInspector.isRenamed(barMethod));
    assertFalse(graphInspector.isPresent(bazMethod));
  }
}
