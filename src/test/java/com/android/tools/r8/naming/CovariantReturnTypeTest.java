// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.StringConsumer.FileConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.File;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class CovariantReturnTypeTest extends TestBase {

  @Test
  public void test() throws Exception {
    String[] returnNullByteCode =
        new String[] { ".limit stack 1", ".limit locals 1", "aconst_null", "areturn" };

    // This test class is generated using Jasmin to have uses of covariant return types which
    // are not just the javac generated bridges (javac would complain that the methods have the
    // same erasure).
    JasminBuilder appBuilder = new JasminBuilder();
    ClassBuilder classBuilder = appBuilder.addClass("package.TestClass");

    classBuilder.addVirtualMethod("method1", "Ljava/lang/Object;", returnNullByteCode);
    classBuilder.addVirtualMethod("method1", "Lpackage/A;", returnNullByteCode);
    classBuilder.addVirtualMethod("method1", "Lpackage/B;", returnNullByteCode);

    classBuilder.addVirtualMethod("method2", "Lpackage/A;", returnNullByteCode);
    classBuilder.addVirtualMethod("method2", "Ljava/lang/Object;", returnNullByteCode);
    classBuilder.addVirtualMethod("method2", "Lpackage/B;", returnNullByteCode);

    classBuilder.addVirtualMethod("method3", "Lpackage/A;", returnNullByteCode);
    classBuilder.addVirtualMethod("method3", "Lpackage/B;", returnNullByteCode);
    classBuilder.addVirtualMethod("method3", "Ljava/lang/Object;", returnNullByteCode);

    appBuilder.addInterface("package.A");
    appBuilder.addInterface("package.B");

    Path proguardMapPath = File.createTempFile("mapping", ".txt", temp.getRoot()).toPath();
    AndroidApp output =
        compileWithR8(
            appBuilder.build(),
            keepMainProguardConfiguration("package.TestClass"),
            options -> {
              options.enableTreeShaking = false;
              options.proguardMapConsumer = new FileConsumer(proguardMapPath);
            });

    CodeInspector inspector = new CodeInspector(output, proguardMapPath);
    ClassSubject clazz = inspector.clazz("package.TestClass");
    assertThat(clazz, isPresent());

    Map<String, Set<MethodSubject>> methodSubjectsByName = new HashMap<>();
    for (String name : ImmutableList.of("method1", "method2", "method3")) {
      methodSubjectsByName.put(
          name,
          ImmutableSet.of(
              clazz.method("java.lang.Object", name, ImmutableList.of()),
              clazz.method("package.A", name, ImmutableList.of()),
              clazz.method("package.B", name, ImmutableList.of())));
    }

    Set<String> minifiedMethodNames = new HashSet<>();
    for (Set<MethodSubject> methodSubjects : methodSubjectsByName.values()) {
      for (MethodSubject methodSubject : methodSubjects) {
        assertThat(methodSubject, isRenamed());
        minifiedMethodNames.add(methodSubject.getFinalName());
      }
    }
    assertEquals(3, minifiedMethodNames.size());
  }
}
