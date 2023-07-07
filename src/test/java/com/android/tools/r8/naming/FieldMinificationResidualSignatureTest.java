// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldMinificationResidualSignatureTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withSystemRuntime().build();
  }

  @Test
  public void testR8() throws Exception {
    Box<FieldReference> minifiedMainField = new Box<>();
    Box<FieldReference> minifiedBField = new Box<>();
    String proguardMap =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .setMinApi(AndroidApiLevel.B)
            .addKeepMainRule(Main.class)
            .addKeepAllClassesRuleWithAllowObfuscation()
            .compile()
            .inspect(
                inspector -> {
                  minifiedMainField.set(
                      inspector.clazz(Main.class).allFields().get(0).getFinalReference());
                  minifiedBField.set(
                      inspector.clazz(B.class).allFields().get(0).getFinalReference());
                })
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("Hello World!")
            .proguardMap();
    Retracer retracer =
        Retracer.createDefault(
            ProguardMapProducer.fromString(proguardMap), new TestDiagnosticMessagesImpl());
    // TODO(b/280802465): We should emit the residual signature to allow us looking up the original.
    assertEquals(
        0,
        retracer.retraceField(minifiedBField.get()).stream()
            .filter(rfe -> rfe.getField().isKnown())
            .count());
    assertEquals(
        0,
        retracer.retraceField(minifiedMainField.get()).stream()
            .filter(rfe -> rfe.getField().isKnown())
            .count());
  }

  public static class Main {

    private static final Container a = new Container("Hello ");

    public static void main(String[] args) {
      System.out.println(a.getMessage() + B.worldContainer.getMessage());
    }
  }

  public static class Container {

    private final String message;

    public Container(String message) {
      this.message = message;
    }

    public String getMessage() {
      return message;
    }
  }

  public static class B {

    public static final Container worldContainer = new Container("World!");
  }
}
