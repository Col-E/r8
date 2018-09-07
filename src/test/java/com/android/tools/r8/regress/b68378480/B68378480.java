// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b68378480;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;


class SuperClass {
  SuperClass(double a, double b, Object c) {
  }
}

class SubClass extends SuperClass {
  SubClass(double a, double b, Object c) {
    super(a, b, c);
  }
}

class Main {
  public static void main(String[] args) {
    new SubClass(1.1, 2.2, null);
  }
}

public class B68378480 {

  private DexCode compileClassesGetSubClassInit(int minApi)
      throws IOException, CompilationFailedException, ExecutionException {
    D8Command.Builder builder = D8Command.builder()
        .setMode(CompilationMode.RELEASE)
        .setMinApiLevel(minApi);
    List<Class> classes = ImmutableList.of(SuperClass.class, SubClass.class, Main.class);
    for (Class c : classes) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(c), Origin.unknown());
    }
    AndroidApp app = ToolHelper.runD8(builder);
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject clazz = inspector.clazz(SubClass.class);
    assertThat(clazz, isPresent());
    MethodSubject method =
        clazz.method("void", "<init>", ImmutableList.of("double", "double", "java.lang.Object"));
    assertThat(method, isPresent());
    DexCode code = method.getMethod().getCode().asDexCode();
    return code;
  }

  @Test
  public void addExtraLocalToConstructor()
      throws IOException, CompilationFailedException, ExecutionException {
    DexCode code = compileClassesGetSubClassInit(AndroidApiLevel.L_MR1.getLevel());
    assertTrue(code.registerSize > code.incomingRegisterSize);
    assertTrue(Arrays.stream(code.instructions).anyMatch((i) -> i instanceof SingleConstant));
  }

  @Test
  public void doNotAddExtraLocalToConstructor()
      throws IOException, CompilationFailedException, ExecutionException {
    DexCode code = compileClassesGetSubClassInit(AndroidApiLevel.M.getLevel());
    assertEquals(code.registerSize, code.incomingRegisterSize);
    assertTrue(Arrays.stream(code.instructions).noneMatch((i) -> i instanceof SingleConstant));
  }
}
