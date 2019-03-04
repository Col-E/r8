// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class NestedStringBuilders {

  public static void main(String... args) {
    System.out.println(concat("one", args[0]) + "two");
  }

  @ForceInline
  public static String concat(String one, String two) {
    return one + two;
  }
}

@RunWith(Parameterized.class)
public class NestedStringBuilderTest extends TestBase {
  private static final Class<?> MAIN = NestedStringBuilders.class;
  private final Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public NestedStringBuilderTest(Backend backend) {
    this.backend = backend;
  }

  private void test(TestCompileResult result) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject main = mainClass.mainMethod();
    long count = Streams.stream(main.iterateInstructions(instructionSubject ->
        instructionSubject.isNewInstance(StringBuilder.class.getTypeName()))).count();
    // TODO(b/113859361): should be 1 after merging StringBuilder's
    assertEquals(2, count);
  }

  @Test
  public void b113859361() throws Exception {
    TestCompileResult result = testForR8(backend)
        .addProgramClasses(MAIN)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .compile();
    test(result);
  }

}