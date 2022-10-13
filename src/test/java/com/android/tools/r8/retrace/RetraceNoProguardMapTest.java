// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static junit.framework.TestCase.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import java.util.Collections;
import java.util.Optional;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RetraceNoProguardMapTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void testNoLine() {
    Retracer retracer =
        Retracer.createDefault(ProguardMapProducer.fromString(""), new DiagnosticsHandler() {});
    MethodReference methodReference =
        Reference.method(Reference.classFromTypeName("a"), "foo", Collections.emptyList(), null);
    Optional<RetraceMethodElement> first =
        retracer.retraceMethod(methodReference).stream().findFirst();
    assertTrue(first.isPresent());
    assertEquals(methodReference, first.get().getRetracedMethod().asKnown().getMethodReference());
  }
}
