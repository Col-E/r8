// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.SoftVerificationErrorJarGenerator.EXISTING_API_METHOD_NAME;
import static com.android.tools.r8.SoftVerificationErrorJarGenerator.NEW_API_CLASS_METHOD_NAME;
import static com.android.tools.r8.SoftVerificationErrorJarGenerator.NEW_API_CLASS_NAME;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.SoftVerificationErrorJarGenerator.ApiCallerName;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ApkUtils;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;

public class SoftVerificationErrorJarRunner extends TestBase {

  public static Path DUMP_PATH =
      Paths.get("third_party", "api-outlining", "simple-app-dump", "simple-app-dump.zip");
  public static Path APK_PATH =
      Paths.get("third_party", "api-outlining", "simple-app-dump", "app-release-unsigned.apk");

  private final int numberOfClasses;
  private final boolean isOutlined;

  public SoftVerificationErrorJarRunner(int numberOfClasses, boolean isOutlined) {
    this.numberOfClasses = numberOfClasses;
    this.isOutlined = isOutlined;
  }

  public static void main(String[] args) throws Exception {
    new SoftVerificationErrorJarRunner(1000, false).runTest();
  }

  public void runTest() throws Exception {

    temp.create();

    Path tempFolder = temp.newFolder().toPath();
    Path outlineJar = tempFolder.resolve("outlined.jar");

    SoftVerificationErrorJarGenerator.createJar(
        outlineJar,
        numberOfClasses,
        isOutlined,
        ApiCallerName.CONSTRUCT_AND_CALL_UNKNOWN,
        NEW_API_CLASS_NAME,
        NEW_API_CLASS_METHOD_NAME,
        EXISTING_API_METHOD_NAME);

    ZipUtils.unzip(DUMP_PATH.toString(), tempFolder.toFile());

    File filteredProgramFolder = temp.newFolder();
    BooleanBox seenTestRunner = new BooleanBox();
    ZipUtils.unzip(
        tempFolder.resolve("program.jar"),
        filteredProgramFolder.toPath(),
        zipEntry -> {
          if (zipEntry.getName().equals("com/example/softverificationsample/TestRunner.class")) {
            seenTestRunner.set();
            return false;
          }
          return true;
        });

    assertTrue(seenTestRunner.get());

    Path filteredProgramJar = tempFolder.resolve("filtered_program.jar");
    ZipUtils.zip(filteredProgramJar, filteredProgramFolder.toPath());

    // Build the app with R8.
    Path output =
        testForR8(Backend.DEX)
            .addProgramFiles(outlineJar, filteredProgramJar)
            .addClasspathFiles(tempFolder.resolve("classpath.jar"))
            .addLibraryFiles(tempFolder.resolve("library.jar"))
            // TODO(b/187496508): Modify keep rules to allow inlining and keep test code.
            .addKeepRuleFiles(tempFolder.resolve("proguard.config"))
            .addKeepRules("-keep class com.example.softverificationsample.* { *; }")
            .setMinApi(AndroidApiLevel.M)
            .allowUnusedProguardConfigurationRules()
            .allowUnusedDontWarnPatterns()
            .allowDiagnosticInfoMessages()
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    Path finalApk = tempFolder.resolve("app-release-final.apk");
    ProcessResult processResult = ApkUtils.apkMasseur(APK_PATH, output, finalApk);
    // TODO(mkroghj): Figure out to have this command succeed when installing the apk
    assertEquals(0, processResult.exitCode);
  }

  private void inspect(CodeInspector inspector) {
    String name =
        "com.example.softverificationsample."
            + (isOutlined ? "ApiCallerOutlined" : "ApiCallerInlined")
            + (numberOfClasses - 1);
    ClassSubject clazz = inspector.clazz(name);
    assertThat(clazz, isPresent());
    if (isOutlined) {
      ClassSubject apiCallerInlined =
          inspector.clazz("com.example.softverificationsample.ApiCallerInlined");
      assertThat(apiCallerInlined, isPresent());
    }
  }
}
