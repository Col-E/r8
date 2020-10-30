// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.desugaredlibrary;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class DesugaredLibraryDeterminismTest extends DesugaredLibraryTestBase {
  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().build();
  }

  public DesugaredLibraryDeterminismTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testDeterminism() throws Exception {
    AndroidApiLevel minApiLevel = parameters.getRuntime().asDex().getMinApiLevel();
    Assume.assumeTrue(minApiLevel.isLessThan(AndroidApiLevel.O));
    Path libDexFile1 = buildDesugaredLibraryToBytes(minApiLevel);
    Path libDexFile2 = buildDesugaredLibraryToBytes(minApiLevel);
    assertIdenticalInspectors(libDexFile1, libDexFile2);
    assertArrayEquals(getDexBytes(libDexFile1), getDexBytes(libDexFile2));
  }

  private void assertIdenticalInspectors(Path libDexFile1, Path libDexFile2) throws IOException {
    CodeInspector i1 = new CodeInspector(libDexFile1.resolve("classes.dex"));
    CodeInspector i2 = new CodeInspector(libDexFile2.resolve("classes.dex"));
    assertEquals(i1.allClasses().size(), i2.allClasses().size());
    Map<DexEncodedMethod, DexEncodedMethod> diffs = new IdentityHashMap<>();
    for (FoundClassSubject clazz1 : i1.allClasses()) {
      ClassSubject clazz = i2.clazz(clazz1.getOriginalName());
      assertTrue(clazz.isPresent());
      FoundClassSubject clazz2 = clazz.asFoundClassSubject();
      assertEquals(clazz1.allMethods().size(), clazz2.allMethods().size());
      for (FoundMethodSubject method1 : clazz1.allMethods()) {
        MethodSubject method = clazz2.method(method1.asMethodReference());
        assertTrue(method.isPresent());
        FoundMethodSubject method2 = method.asFoundMethodSubject();
        if (method1.hasCode()) {
          assertTrue(method2.hasCode());
          if (!(method1
              .getMethod()
              .getCode()
              .toString()
              .equals(method2.getMethod().getCode().toString()))) {
            diffs.put(method1.getMethod(), method2.getMethod());
          }
        }
      }
    }
    assertTrue(printDiffs(diffs), diffs.isEmpty());
  }

  private String printDiffs(Map<DexEncodedMethod, DexEncodedMethod> diffs) {
    StringBuilder sb = new StringBuilder();
    sb.append("The following methods had differences from one dex file to the other (")
        .append(diffs.size())
        .append("):\n");
    diffs.forEach(
        (m1, m2) -> {
          sb.append(m1.toSourceString()).append("\n");
          String[] lines1 = m1.getCode().toString().split("\n");
          String[] lines2 = m2.getCode().toString().split("\n");
          if (lines1.length != lines2.length) {
            sb.append("Different number of lines.");
            sb.append("\n");
          } else {
            for (int i = 0; i < lines1.length; i++) {
              if (!lines1[i].equals(lines2[i])) {
                sb.append(lines1[i]);
                sb.append("\n");
                sb.append(lines2[i]);
                sb.append("\n");
                return;
              }
            }
          }
        });
    return sb.toString();
  }

  private byte[] getDexBytes(Path libDexFile) throws IOException {
    return Files.readAllBytes(libDexFile.resolve("classes.dex"));
  }

  private Path buildDesugaredLibraryToBytes(AndroidApiLevel minApiLevel) throws IOException {
    Path lib1 = buildDesugaredLibrary(minApiLevel);
    Path unzipped1 = temp.newFolder().toPath();
    ZipUtils.unzip(lib1.toString(), unzipped1.toFile());
    return unzipped1;
  }
}
