// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b111960171;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

class TestClass {
  public void f(int x, double y, double u, double v, List<Object> w) {
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    f(x, y, u, v, w);
    w.add(g(y, u, v));
  }

  public Object g(double y, double u, double v) {
    return null;
  }
}

public class B111960171 {

  private MethodSubject compileTestClassAndGetMethod(int apiLevel)
      throws IOException, CompilationFailedException, ExecutionException {
    AndroidApp app =
        ToolHelper.runD8(
            D8Command.builder()
                .addClassProgramData(ToolHelper.getClassAsBytes(TestClass.class), Origin.unknown())
                .setMinApiLevel(apiLevel));
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(TestClass.class);
    assertThat(clazz, isPresent());
    MethodSubject method =
        clazz.method(
            "void", "f", ImmutableList.of("int", "double", "double", "double", "java.util.List"));
    assertThat(method, isPresent());
    return method;
  }

  @Test
  public void disableDex2OatInliningWithTryCatch()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = compileTestClassAndGetMethod(AndroidApiLevel.M.getLevel());
    assertTrue(method.getMethod().getCode().asDexCode().handlers != null);
  }

  @Test
  public void dontDisableDex2OatInliningWithTryCatch()
      throws IOException, CompilationFailedException, ExecutionException {
    MethodSubject method = compileTestClassAndGetMethod(AndroidApiLevel.N.getLevel());
    assertTrue(method.getMethod().getCode().asDexCode().handlers == null);
  }
}
