// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.applymapping;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ProguardTestRunResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class ToBeRenamedClass {
  void toBeRenamedMethod() {
    System.out.println("foo");
  }
}

class WhenToApplyTestRunner {
  public static void main(String[] args) {
    ToBeRenamedClass instance = new ToBeRenamedClass();
    instance.toBeRenamedMethod();
    System.out.println(instance.getClass().getSimpleName());
  }
}

@RunWith(Parameterized.class)
public class WhenToApplyTest extends TestBase {

  @ClassRule
  public static TemporaryFolder temporaryFolder = ToolHelper.getTemporaryFolderForTest();

  private static Class<?> MAIN = WhenToApplyTestRunner.class;
  private static String RENAMED_CLASS_NAME =
      ToBeRenamedClass.class.getPackage().getName() + ".ABC";
  private static String NORMAL_OUTPUT = StringUtils.lines("foo", "ToBeRenamedClass");
  private static String APPLIED_OUTPUT = StringUtils.lines("foo", "ABC");
  private static String RENAMED_OUTPUT = StringUtils.lines("foo", "a");

  private static Path mappingFile;
  private static Path configuration;
  private boolean minification;

  @Parameterized.Parameters(name = "minification: {0}")
  public static Boolean[] data() {
    return BooleanUtils.values();
  }

  public WhenToApplyTest(boolean minification) {
    this.minification = minification;
  }

  @BeforeClass
  public static void setUpMappingFile() throws Exception {
    mappingFile = temporaryFolder.newFile("mapping.txt").toPath();
    FileUtils.writeTextFile(mappingFile, StringUtils.lines(
        ToBeRenamedClass.class.getTypeName() + " -> " + RENAMED_CLASS_NAME + ":",
        "  void toBeRenamedMethod() -> abc"));
    configuration = temporaryFolder.newFile("pg.conf").toPath();
    FileUtils.writeTextFile(configuration, StringUtils.lines(
        "-dontoptimize",
        "-applymapping " + mappingFile
    ));
  }

  @Test
  public void testProguard() throws Exception {
    ProguardTestRunResult result = testForProguard()
        .addProgramClasses(ToBeRenamedClass.class, MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRuleFiles(configuration)
        .minification(minification)
        .compile().run(MAIN);
    if (minification) {
      result.assertSuccessWithOutput(APPLIED_OUTPUT);
    } else {
      result.assertSuccessWithOutput(NORMAL_OUTPUT);
    }
    result.inspect(inspector -> {
      ClassSubject classSubject = inspector.clazz(ToBeRenamedClass.class);
      assertThat(classSubject, isPresent());
      // As renaming won't happen again, we can use the original name to search for the method.
      MethodSubject methodSubject = classSubject.uniqueMethodWithName("toBeRenamedMethod");
      assertThat(methodSubject, isPresent());
      String methodName =
          minification
              ? "abc"                // mapped name with minification
              : "toBeRenamedMethod"; // original name without minification
      assertEquals(methodName, methodSubject.getFinalName());
    });
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result = testForR8(Backend.DEX)
        .addProgramClasses(ToBeRenamedClass.class, MAIN)
        .addKeepMainRule(MAIN)
        .addKeepRuleFiles(configuration)
        .minification(minification)
        .compile().run(MAIN);
    if (minification) {
      result.assertSuccessWithOutput(RENAMED_OUTPUT);
    } else {
      result.assertSuccessWithOutput(APPLIED_OUTPUT);
    }
    result.inspect(inspector -> {
      ClassSubject classSubject = inspector.clazz(RENAMED_CLASS_NAME);
      assertThat(classSubject, isPresent());
      // Mapped name will be regarded as an original name if minification is disabled.
      String methodName =
          minification
              ? "toBeRenamedMethod" // original name
              : "abc";              // mapped name without minification
      MethodSubject methodSubject = classSubject.uniqueMethodWithName(methodName);
      assertThat(methodSubject, isPresent());
      methodName =
          minification
              ? "a"    // minified name
              : "abc"; // mapped name without minification
      assertEquals(methodName, methodSubject.getFinalName());
    });
  }

}
