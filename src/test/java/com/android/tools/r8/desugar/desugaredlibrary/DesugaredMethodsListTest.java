// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_LEGACY;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_MINIMAL;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK11_PATH;
import static com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification.JDK8;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.lint.DesugaredMethodsList;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugaredMethodsListTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;
  private final LibraryDesugaringSpecification libraryDesugaringSpecification;

  private List<String> lintContents;

  @Parameters(name = "{0}, {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().build(),
        ImmutableList.of(JDK8, JDK11_MINIMAL, JDK11, JDK11_PATH, JDK11_LEGACY));
  }

  public DesugaredMethodsListTest(
      TestParameters parameters, LibraryDesugaringSpecification libraryDesugaringSpecification) {
    this.parameters = parameters;
    this.libraryDesugaringSpecification = libraryDesugaringSpecification;
  }

  private boolean supportsAllMethodsOf(String type) {
    return lintContents.contains(type);
  }

  private void checkFileContent(AndroidApiLevel minApiLevel, Path lintFile) throws Exception {
    // Just do some light probing in the generated lint files.
    lintContents = FileUtils.readAllLines(lintFile);

    // All methods supported on BiFunction with maintain prefix.
    assertEquals(
        minApiLevel.isLessThan(AndroidApiLevel.N),
        supportsAllMethodsOf("java/util/function/BiFunction"));

    assertEquals(
        minApiLevel.isLessThan(AndroidApiLevel.O)
            && libraryDesugaringSpecification != JDK11_MINIMAL,
        supportsAllMethodsOf("java/time/MonthDay"));

    // File should be sorted.
    List<String> sorted = new ArrayList<>(lintContents);
    sorted.sort(Comparator.naturalOrder());
    assertEquals(lintContents, sorted);
  }

  @Test
  public void testLint() throws Exception {
    Path output = temp.newFile("lint.txt").toPath();
    Path jdkLibJar =
        libraryDesugaringSpecification == JDK8
            ? ToolHelper.DESUGARED_JDK_8_LIB_JAR
            : LibraryDesugaringSpecification.getTempLibraryJDK11Undesugar();

    AndroidApiLevel minApi = parameters.getRuntime().asDex().getMinApiLevel();
    DesugaredMethodsList.main(
        new String[] {
          String.valueOf(minApi.getLevel()),
          libraryDesugaringSpecification.getSpecification().toString(),
          jdkLibJar.toString(),
          output.toString(),
          ToolHelper.getAndroidJar(AndroidApiLevel.U).toString()
        });
    checkFileContent(minApi, output);
  }
}
