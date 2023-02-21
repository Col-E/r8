// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import org.junit.Test;

public class DistinguishExceptionClassesTest extends HorizontalClassMergingTestBase {
  public DistinguishExceptionClassesTest(TestParameters parameters) {
    super(parameters);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("test success")
        .inspect(
            codeInspector -> {
              assertThat(codeInspector.clazz(Exception1.class), isPresent());
              assertThat(codeInspector.clazz(Exception2.class), isPresent());
            });
  }

  public static class Exception1 extends Exception {}

  public static class Exception2 extends Exception {}

  public static class Main {
    public static void main(String[] args) {
      try {
        try {
          if (System.currentTimeMillis() > 0) {
            throw new Exception2();
          } else {
            throw new Exception1();
          }
        } catch (Exception1 ex) {
          System.out.println("test failed");
        }
      } catch (Exception2 ex) {
        System.out.println("test success");
      }
    }
  }
}
