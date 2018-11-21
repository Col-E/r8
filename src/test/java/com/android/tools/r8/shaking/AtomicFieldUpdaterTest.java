// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class AtomicFieldUpdaterTest extends TestBase {

  private final Backend backend;

  @Parameters(name = "backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public AtomicFieldUpdaterTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    AndroidApp input =
        readClasses(AtomicFieldUpdaterTestClass.class, AtomicFieldUpdaterTestClass.A.class);
    Path proguardMapPath = File.createTempFile("mapping", ".txt", temp.getRoot()).toPath();
    AndroidApp output =
        compileWithR8(
            input,
            keepMainProguardConfiguration(AtomicFieldUpdaterTestClass.class),
            options -> options.proguardMapConsumer = new FileConsumer(proguardMapPath),
            backend);
    // Verify that the field is still there.
    CodeInspector inspector = new CodeInspector(output, proguardMapPath);
    ClassSubject classSubject = inspector.clazz(AtomicFieldUpdaterTestClass.A.class.getName());
    assertThat(classSubject, isRenamed());
    assertThat(classSubject.field("int", "field"), isRenamed());
    // Check that the code runs.
    assertEquals(
        runOnJava(AtomicFieldUpdaterTestClass.class),
        runOnVM(output, AtomicFieldUpdaterTestClass.class.getName(), backend));
  }
}

class AtomicFieldUpdaterTestClass {

  public static void main(String[] args) {
    // Throws NoSuchFieldException if field is removed by tree shaking, or if the string "field" is
    // not renamed as a result of minification.
    AtomicIntegerFieldUpdater.newUpdater(AtomicFieldUpdaterTestClass.A.class, "field");
    System.out.println("Test succeeded");
  }

  static class A {
    volatile int field;
  }
}
