// Copyright (c) 2018 the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b113326860;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.Sput;
import com.android.tools.r8.code.SputBoolean;
import com.android.tools.r8.code.SputObject;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.origin.Origin;
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

class TestClassDoOptimize {
  private static boolean b = false;
  private static int i = 3;
  private static Class clazz;
  static {
    clazz = TestClassDoOptimize.class;
    b = true;
    i = 42;
  }
}

class TestClassDoNotOptimize {

  private static boolean initialized = false;
  private static final TestClassDoNotOptimize INSTANCE;

  TestClassDoNotOptimize() {
    if (!initialized) {
      System.out.println("Not initialized as expected");
    }
  }

  static {
    INSTANCE = new TestClassDoNotOptimize();
    initialized = true;
  }
}

class TestClassDoNotOptimize2 {
  static boolean b = false;
  static {
    boolean forcingOtherClassInit = TestClassDoNotOptimize3.b;
    b = true;
  }

  static void m() {
    System.out.println(b);
  }
}

class TestClassDoNotOptimize3 {
  static boolean b = false;
  static {
    TestClassDoNotOptimize2.m();
  }
}

class TestDoWhileLoop {
  static int i = 0;
  static int j = 0;
  static {
    do {
      i = 42;
      System.out.println(i);
      j = j + 1;
      i = 10;
    } while (j < 10);
  }

  public static void main(String[] args) {
    System.out.println(TestDoWhileLoop.i);
  }
}

public class B113326860 {

  private CodeInspector compileTestClasses(List<Class> classes)
      throws IOException, CompilationFailedException, ExecutionException {
    D8Command.Builder builder = D8Command.builder().setMode(CompilationMode.RELEASE);
    for (Class c : classes) {
      builder.addClassProgramData(ToolHelper.getClassAsBytes(c), Origin.unknown());
    }
    AndroidApp app = ToolHelper.runD8(builder);
    return new CodeInspector(app);
  }

  @Test
  public void optimizedClassInitializer()
      throws IOException, CompilationFailedException, ExecutionException {
    CodeInspector inspector = compileTestClasses(ImmutableList.of(TestClassDoOptimize.class));
    ClassSubject clazz = inspector.clazz(TestClassDoOptimize.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "<clinit>", ImmutableList.of());
    assertThat(method, isPresent());
    assertFalse(Arrays.stream(method.getMethod().getCode().asDexCode().instructions)
        .anyMatch(i -> i instanceof SputBoolean || i instanceof Sput));
    assertTrue(Arrays.stream(method.getMethod().getCode().asDexCode().instructions)
        .anyMatch(i -> i instanceof SputObject));
  }

  @Test
  public void nonOptimizedClassInitializer()
      throws ExecutionException, CompilationFailedException, IOException {
    CodeInspector inspector =
        compileTestClasses(ImmutableList.of(TestClassDoNotOptimize.class));
    ClassSubject clazz = inspector.clazz(TestClassDoNotOptimize.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "<clinit>", ImmutableList.of());
    assertThat(method, isPresent());
    assertTrue(Arrays.stream(method.getMethod().getCode().asDexCode().instructions)
        .anyMatch(i -> i instanceof SputBoolean));
  }

  @Test
  public void nonOptimizedClassInitializer2()
      throws ExecutionException, CompilationFailedException, IOException {
    CodeInspector inspector = compileTestClasses(
        ImmutableList.of(TestClassDoNotOptimize2.class, TestClassDoNotOptimize3.class));
    ClassSubject clazz = inspector.clazz(TestClassDoNotOptimize2.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "<clinit>", ImmutableList.of());
    assertThat(method, isPresent());
    assertTrue(Arrays.stream(method.getMethod().getCode().asDexCode().instructions)
        .anyMatch(i -> i instanceof SputBoolean));
  }

  @Test
  public void doWhileLoop() throws ExecutionException, CompilationFailedException, IOException {
    CodeInspector inspector = compileTestClasses(ImmutableList.of(TestDoWhileLoop.class));
    ClassSubject clazz = inspector.clazz(TestDoWhileLoop.class);
    assertThat(clazz, isPresent());
    MethodSubject method = clazz.method("void", "<clinit>", ImmutableList.of());
    assertThat(method, isPresent());
    // Leave the const 42 and the assignment in there!
    assertTrue(Arrays.stream(method.getMethod().getCode().asDexCode().instructions)
        .anyMatch(i -> i instanceof SingleConstant && (((SingleConstant) i).decodedValue() == 42)));
  }
}
