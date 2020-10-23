// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.DataEntryResource;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.classmerging.horizontal.EmptyClassTest.B;
import com.android.tools.r8.classmerging.horizontal.EmptyClassTest.Main;
import com.android.tools.r8.classmerging.horizontal.ServiceLoaderTest.A;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.DataResourceConsumerForTesting;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;

public class AdaptResourceFileContentsTest extends HorizontalClassMergingTestBase {
  public AdaptResourceFileContentsTest(
      TestParameters parameters, boolean enableHorizontalClassMerging) {
    super(parameters, enableHorizontalClassMerging);
  }

  @Test
  public void testR8() throws Exception {
    DataResourceConsumerForTesting dataResourceConsumer = new DataResourceConsumerForTesting();
    CodeInspector codeInspector =
        testForR8(parameters.getBackend())
            .addInnerClasses(getClass())
            .addKeepMainRule(Main.class)
            .addOptionsModification(
                options -> options.enableHorizontalClassMerging = enableHorizontalClassMerging)
            .addOptionsModification(options -> options.dataResourceConsumer = dataResourceConsumer)
            .enableNeverClassInliningAnnotations()
            .addDataEntryResources(
                DataEntryResource.fromString(
                    "foo.txt", Origin.unknown(), A.class.getTypeName(), B.class.getTypeName()))
            .addKeepRules("-adaptresourcefilecontents foo.txt")
            .setMinApi(parameters.getApiLevel())
            .addHorizontallyMergedClassesInspectorIf(
                enableHorizontalClassMerging,
                inspector -> inspector.assertMergedInto(B.class, A.class))
            .compile()
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("a", "b")
            .inspector();

    ClassSubject aClassSubject = codeInspector.clazz(A.class);
    assertThat(aClassSubject, isPresent());

    ClassSubject bClassSubject = codeInspector.clazz(B.class);
    assertThat(bClassSubject, notIf(isPresent(), enableHorizontalClassMerging));

    // Check that the class name has been rewritten.
    String newClassBName =
        (enableHorizontalClassMerging ? aClassSubject : bClassSubject).getFinalName();
    assertEquals(
        dataResourceConsumer.get("foo.txt"),
        ImmutableList.of(aClassSubject.getFinalName(), newClassBName));
  }

  @NeverClassInline
  public static class A {
    public A() {
      System.out.println("a");
    }
  }

  @NeverClassInline
  public static class B {
    public B() {
      System.out.println("b");
    }
  }

  public static class Main {
    public static void main(String[] args) {
      new A();
      new B();
    }
  }
}
