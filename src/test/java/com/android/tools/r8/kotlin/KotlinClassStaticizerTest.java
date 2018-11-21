// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

public class KotlinClassStaticizerTest extends AbstractR8KotlinTestBase {

  @Test
  public void testCompanionAndRegularObjects() throws Exception {
    assumeTrue("Only work with -allowaccessmodification", allowAccessModification);
    final String mainClassName = "class_staticizer.MainKt";

    // Without class staticizer.
    runTest("class_staticizer", mainClassName, false, (app) -> {
      CodeInspector inspector = new CodeInspector(app);
      assertTrue(inspector.clazz("class_staticizer.Regular$Companion").isPresent());
      assertTrue(inspector.clazz("class_staticizer.Derived$Companion").isPresent());

      ClassSubject utilClass = inspector.clazz("class_staticizer.Util");
      assertTrue(utilClass.isPresent());
      AtomicInteger nonStaticMethodCount = new AtomicInteger();
      utilClass.forAllMethods(method -> {
        if (!method.isStatic()) {
          nonStaticMethodCount.incrementAndGet();
        }
      });
      assertEquals(4, nonStaticMethodCount.get());
    });

    // With class staticizer.
    runTest("class_staticizer", mainClassName, true, (app) -> {
      CodeInspector inspector = new CodeInspector(app);
      assertFalse(inspector.clazz("class_staticizer.Regular$Companion").isPresent());
      assertFalse(inspector.clazz("class_staticizer.Derived$Companion").isPresent());

      ClassSubject utilClass = inspector.clazz("class_staticizer.Util");
      assertTrue(utilClass.isPresent());
      utilClass.forAllMethods(method -> assertTrue(method.isStatic()));
    });
  }

  protected void runTest(String folder, String mainClass,
      boolean enabled, AndroidAppInspector inspector) throws Exception {
    runTest(
        folder, mainClass, null,
        options -> {
          options.enableClassInlining = false;
          options.enableClassStaticizer = enabled;
        }, inspector);
  }
}
