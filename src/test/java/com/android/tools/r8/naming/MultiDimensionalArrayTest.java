// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.ProgramConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

class ToBeRenamedForMultiDimensionalArrayTest {
  private int counter = 1;
  final int field;

  ToBeRenamedForMultiDimensionalArrayTest() {
    field = counter++;
  }

  String foo() {
    return String.valueOf(field);
  }
}

class MultiDimensionalArrayTestMain {
  public static void main(String[] args) {
    int x = 3;
    int y = 4;
    ToBeRenamedForMultiDimensionalArrayTest[][] multiDimensionalArray =
        new ToBeRenamedForMultiDimensionalArrayTest[x][y];
    for (int i = 0; i < x; i++) {
      for (int j = 0; j < y; j++) {
        multiDimensionalArray[i][j] = new ToBeRenamedForMultiDimensionalArrayTest();
      }
    }
    for (int i = 0; i < x; i++) {
      for (int j = 0; j < y; j++) {
        System.out.print(multiDimensionalArray[i][j].foo());
      }
    }
  }
}

public class MultiDimensionalArrayTest extends TestBase {
  List<byte[]> classes;

  @Before
  public void setUp() throws Exception {
    classes = ImmutableList.of(
        ToolHelper.getClassAsBytes(ToBeRenamedForMultiDimensionalArrayTest.class),
        ToolHelper.getClassAsBytes(MultiDimensionalArrayTestMain.class));
  }

  @Test
  public void test() throws Exception {
    Path mapPath = temp.newFile("test-mapping.txt").toPath();
    List<String> pgMap = ImmutableList.of(
        "com.android.tools.r8.naming.ToBeRenamedForMultiDimensionalArrayTest -> X:",
        "  java.lang.String foo() -> bar"
    );
    FileUtils.writeTextFile(mapPath, pgMap);

    AndroidApp app = buildAndroidApp(classes);
    ProgramConsumer programConsumer = ClassFileConsumer.emptyConsumer();
    Path library = ToolHelper.getJava8RuntimeJar();
    R8Command.Builder builder =
        ToolHelper.prepareR8CommandBuilder(app, programConsumer).addLibraryFiles(library);
    builder.addProguardConfiguration(
        ImmutableList.of(
            keepMainProguardConfiguration(MultiDimensionalArrayTestMain.class),
            "-applymapping " + mapPath,
            "-allowaccessmodification", // so that 'X' is no longer package-private.
            "-dontobfuscate"), // to use the renamed names in test-mapping.txt
        Origin.unknown());
    AndroidApp processedApp = ToolHelper.runR8(builder.build());

    String main = MultiDimensionalArrayTestMain.class.getCanonicalName();
    ProcessResult javaOutput = runOnJavaRaw(main, classes, ImmutableList.of());
    assertEquals(0, javaOutput.exitCode);
    ProcessResult output = runOnJavaRaw(processedApp, main, ImmutableList.of());
    assertEquals(0, output.exitCode);
    assertEquals(javaOutput.stdout.trim(), output.stdout.trim());

    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject mainSubject = inspector.clazz(MultiDimensionalArrayTestMain.class);
    assertThat(mainSubject, isPresent());
    ClassSubject target = inspector.clazz("X");
    assertThat(target, isPresent());
  }
}
