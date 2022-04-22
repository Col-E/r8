// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import java.lang.invoke.MethodHandle;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokePolymorphicResolutionTest extends TestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InvokePolymorphicResolutionTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testResolution() throws Exception {
    // Note: this could just as well resolve without liveness.
    AppView<? extends AppInfoWithClassHierarchy> appView =
        TestAppViewBuilder.builder()
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .buildWithLiveness();

    // An exact resolution will find invokeExact.
    MethodReference invokeExact =
        methodFromMethod(MethodHandle.class.getMethod("invokeExact", Object[].class));
    MethodResolutionResult resolution1 =
        appView
            .appInfo()
            .resolveMethodLegacy(buildMethod(invokeExact, appView.dexItemFactory()), false);
    assertFalse(resolution1.isFailedResolution());

    // An inexact signature should also find invokeExact.
    MethodReference inexactInvokeExact =
        Reference.method(
            invokeExact.getHolderClass(),
            invokeExact.getMethodName(),
            Collections.singletonList(Reference.array(classFromClass(getClass()), 1)),
            invokeExact.getReturnType());
    MethodResolutionResult resolution2 =
        appView
            .appInfo()
            .resolveMethodLegacy(buildMethod(inexactInvokeExact, appView.dexItemFactory()), false);
    assertFalse(resolution2.isFailedResolution());

    // The should both be the same MethodHandle.invokeExact method.
    assertEquals(resolution1.getSingleTarget(), resolution2.getSingleTarget());
  }
}
