// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.reflection;
import static com.android.tools.r8.references.Reference.fieldFromField;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.graphinspector.GraphInspector.QueryNode;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldAccessTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private boolean isInvokeGetField(InstructionSubject instruction) {
    return
        instruction.isInvoke()
            && instruction.getMethod().qualifiedName().equals("java.lang.Class.getField");
  }

  private void runTest(Class<?> testClass) throws Exception {
    MethodReference mainMethod =
        methodFromMethod(testClass.getDeclaredMethod("main", String[].class));
    FieldReference fooField = fieldFromField(testClass.getDeclaredField("foo"));

    testForR8(parameters.getBackend())
        .enableGraphInspector()
        .addProgramClasses(testClass)
        .addKeepMainRule(testClass)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), testClass)
        .inspectGraph(
            inspector -> {
              // The only root should be the keep annotation rule.
              assertEquals(1, inspector.getRoots().size());
              QueryNode root = inspector.rule(Origin.unknown(), 1, 1).assertRoot();

              inspector.method(mainMethod).assertNotRenamed().assertKeptBy(root);
              inspector.field(fooField).assertRenamed();
            })
        .inspect(
            inspector -> {
              Assert.assertTrue(
                  inspector
                      .clazz(testClass)
                      .uniqueMethodWithOriginalName("main")
                      .streamInstructions()
                      .anyMatch(this::isInvokeGetField));
            })
        .assertSuccessWithOutput("42");
  }

  @Test
  public void reflectiveGet() throws Exception {
    runTest(FieldAccessTestGet.class);
  }

  @Test
  public void reflectiveGetStatic() throws Exception {
    runTest(FieldAccessTestGetStatic.class);
  }

  @Test
  public void reflectivePut() throws Exception {
    runTest(FieldAccessTestPut.class);
  }

  @Test
  public void reflectivePutStatic() throws Exception {
    runTest(FieldAccessTestPutStatic.class);
  }

  @Test
  public void reflectivePutGet() throws Exception {
    runTest(FieldAccessTestPutGet.class);
  }

  @Test
  public void reflectivePutGetStatic() throws Exception {
    runTest(FieldAccessTestPutGetStatic.class);
  }
}

class FieldAccessTestGet {

  public int foo = 42;

  public static void main(String[] args) throws Exception {
    FieldAccessTestGet obj = new FieldAccessTestGet();
    System.out.print(FieldAccessTestGet.class.getField("foo").getInt(obj));
  }
}

class FieldAccessTestGetStatic {

  public static int foo = 42;

  public static void main(String[] args) throws Exception {
    System.out.print(FieldAccessTestGetStatic.class.getField("foo").getInt(null));
  }
}

class FieldAccessTestPut {

  public int foo;

  public static void main(String[] args) throws Exception {
    FieldAccessTestPut obj = new FieldAccessTestPut();
    FieldAccessTestPut.class.getField("foo").setInt(obj, 42);
    System.out.print(42);
  }
}

class FieldAccessTestPutStatic {

  public static int foo;

  public static void main(String[] args) throws Exception {
    FieldAccessTestPutStatic.class.getField("foo").setInt(null, 42);
    System.out.print(42);
  }
}

class FieldAccessTestPutGet {

  public int foo;

  public static void main(String[] args) throws Exception {
    FieldAccessTestPutGet obj = new FieldAccessTestPutGet();
    FieldAccessTestPutGet.class.getField("foo").setInt(obj, 42);
    System.out.print(FieldAccessTestPutGet.class.getField("foo").getInt(obj));
  }
}

class FieldAccessTestPutGetStatic {

  public static int foo;

  public static void main(String[] args) throws Exception {
    FieldAccessTestPutGetStatic.class.getField("foo").setInt(null, 42);
    System.out.print(FieldAccessTestPutGetStatic.class.getField("foo").getInt(null));
  }
}
