// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class InlineSynthesizedLambdaClass extends TestBase {

  @Test
  public void test() throws Exception {
    AndroidApp input = readClasses(Lambda.class, Lambda.Consumer.class);
    AndroidApp output =
        compileWithR8(
            input,
            String.join(
                System.lineSeparator(),
                keepMainProguardConfiguration(Lambda.class),
                "-allowaccessmodification"),
            options -> options.enableMinification = false);

    // Check that everything has been inlined into main.
    CodeInspector inspector = new CodeInspector(output);
    assertEquals(1, inspector.allClasses().size());

    ClassSubject classSubject = inspector.clazz(Lambda.class);
    assertThat(classSubject, isPresent());
    assertEquals(1, classSubject.allMethods().size());

    // Check that the program gives the expected result.
    assertEquals(runOnJava(Lambda.class), runOnArt(output, Lambda.class));
  }
}

class Lambda {

  interface Consumer<T> {
    void accept(T value);
  }

  public static void main(String... args) {
    load(s -> System.out.println(s));
    load(s -> System.out.println(s));
  }

  public static void load(Consumer<String> c) {
    c.accept("Hello!");
  }
}
