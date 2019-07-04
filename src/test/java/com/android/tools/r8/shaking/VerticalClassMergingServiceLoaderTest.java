// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingServiceLoaderTest extends TestBase {

  public interface A {
    String foo();
  }

  public static class B implements A {

    @Override
    public String foo() {
      return "Hello World!";
    }
  }

  public static class C {

    public static void main(String[] args) {
      System.out.println(ServiceLoader.load(A.class).iterator().next().foo());
    }
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public VerticalClassMergingServiceLoaderTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testShouldNotInlineClass()
      throws IOException, CompilationFailedException, ExecutionException {
    List<String> serviceImplementations = Lists.newArrayList();
    serviceImplementations.add(B.class.getTypeName());

    testForR8(parameters.getBackend())
        .addInnerClasses(VerticalClassMergingServiceLoaderTest.class)
        .addKeepClassRules(B.class)
        .addKeepMainRule(C.class)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(serviceImplementations).getBytes(),
                "META-INF/services/" + A.class.getTypeName(),
                Origin.unknown()))
        .setMinApi(parameters.getRuntime())
        .run(parameters.getRuntime(), C.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            codeInspector -> {
              assert codeInspector.clazz(A.class).isPresent();
            });
  }
}
