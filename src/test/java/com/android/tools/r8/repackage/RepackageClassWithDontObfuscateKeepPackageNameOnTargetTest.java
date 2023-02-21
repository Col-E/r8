// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.CoreMatchers.startsWith;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageClassWithDontObfuscateKeepPackageNameOnTargetTest extends RepackageTestBase {

  private static final String DESTINATION_PACKAGE = "other.package";

  public RepackageClassWithDontObfuscateKeepPackageNameOnTargetTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Override
  protected String getRepackagePackage() {
    return DESTINATION_PACKAGE;
  }

  @Test
  public void testR8() throws Exception {
    String originalPackage = DescriptorUtils.getPackageNameFromBinaryName(binaryName(Foo.class));
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .apply(this::configureRepackaging)
        .addDontObfuscate()
        .addKeepPackageNamesRule(originalPackage)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("Foo::foo()")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(Foo.class);
              assertThat(clazz, isPresent());
              assertThat(clazz.getFinalName(), not(startsWith(DESTINATION_PACKAGE)));
              String finalPackage =
                  DescriptorUtils.getPackageNameFromBinaryName(clazz.getFinalBinaryName());
              assertEquals(originalPackage, finalPackage);
            });
  }

  public static class Main {

    public static void main(String[] args) {
      Foo.foo();
    }
  }

  public static class Foo {

    @NeverInline
    public static void foo() {
      System.out.println("Foo::foo()");
    }
  }
}
