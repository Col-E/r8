// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.d8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestBuilder;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ProguardMapSortByTest extends TestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ProguardMapSortByTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMapSorting() throws Exception {
    Path mappingFile = temp.newFile().toPath();
    // Sort the classes, so that they are in 3 packages _not_ sorted by their original name.
    FileUtils.writeTextFile(
        mappingFile,
        "com.A.a -> " + C.class.getTypeName() + ":",
        "com.A.b -> " + B.class.getTypeName() + ":",
        "com.B.a -> " + D.class.getTypeName() + ":",
        "com.B.b -> " + E.class.getTypeName() + ":",
        "com.C.a -> " + A.class.getTypeName() + ":");
    D8TestBuilder d8TestBuilder =
        testForD8()
            .addOptionsModification(
                internalOptions -> internalOptions.testing.limitNumberOfClassesPerDex = 2);
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.L)) {
      d8TestBuilder.addMainDexListClasses(A.class);
    }
    Path outputDir = temp.newFolder().toPath();
    d8TestBuilder
        .release()
        .addProgramClasses(A.class, B.class, C.class, D.class, E.class)
        .setMinApi(parameters)
        .apply(b -> b.getBuilder().setProguardMapInputFile(mappingFile))
        .run(parameters.getRuntime(), A.class)
        .assertSuccessWithOutputLines("Hello world!")
        .app()
        .writeToDirectory(outputDir, OutputMode.DexIndexed);
    Map<String, String> mapping = new HashMap<>();
    for (String dexFile : ImmutableList.of("classes.dex", "classes2.dex", "classes3.dex")) {
      Path resolvedDexFile = outputDir.resolve(dexFile);
      assertTrue(Files.exists(resolvedDexFile));
      classNamesFromDexFile(resolvedDexFile).forEach(name -> mapping.put(name, dexFile));
    }
    // Check that the classes are grouped by their original package name.
    assertEquals(mapping.get(B.class.getTypeName()), mapping.get(C.class.getTypeName()));
    assertEquals(mapping.get(D.class.getTypeName()), mapping.get(E.class.getTypeName()));
    assertTrue(
        mapping.values().stream().filter(s -> s.equals(mapping.get(A.class.getTypeName()))).count()
            == 1);
  }

  static class A {
    public static void main(String[] args) {
      System.out.println("Hello world!");
    }
  }

  static class B {
    public static void foo() {
      System.out.println("foo");
    }
  }

  static class C {
    public static void bar() {
      System.out.println("bar");
    }
  }

  static class D {
    public static void foobar() {
      System.out.println("foobar");
    }
  }

  static class E {
    public static void barfoo() {
      System.out.println("barfoo");
    }
  }
}
