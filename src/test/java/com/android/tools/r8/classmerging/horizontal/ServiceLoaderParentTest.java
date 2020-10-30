// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.Lists;
import java.util.List;
import java.util.ServiceLoader;
import org.junit.Test;

public class ServiceLoaderParentTest extends HorizontalClassMergingTestBase {
  public ServiceLoaderParentTest(TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    List<String> serviceImplementations = Lists.newArrayList();
    serviceImplementations.add(B.class.getTypeName());
    serviceImplementations.add(C.class.getTypeName());

    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addDataEntryResources(
            DataEntryResource.fromBytes(
                StringUtils.lines(serviceImplementations).getBytes(),
                "META-INF/services/" + A.class.getTypeName(),
                Origin.unknown()))
        .enableNoVerticalClassMergingAnnotations()
        .addOptionsModification(
            options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccess()
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(A.class), isPresent());
              assertThat(codeInspector.clazz(Parent.class), isPresent());
              assertThat(codeInspector.clazz(B.class), isPresent());
              assertThat(codeInspector.clazz(C.class), isPresent());
            });
  }

  @NoVerticalClassMerging
  public interface A {
    String foo();
  }

  public abstract static class Parent implements A {}

  public static class B extends Parent {
    @Override
    public String foo() {
      return "B";
    }
  }

  public static class C extends Parent {
    @Override
    public String foo() {
      return "C";
    }
  }

  public static class Main {
    public static void main(String[] args) {
      for (A a : ServiceLoader.load(A.class)) {
        System.out.println(a.foo());
      }
    }
  }
}
