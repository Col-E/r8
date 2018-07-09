// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.ToolHelper.EXAMPLES_KOTLIN_RESOURCE_DIR;
import static com.android.tools.r8.kotlin.KotlinLambdaMergingTest.kstyle;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.kotlin.KotlinLambdaMergingTest.Group;
import com.android.tools.r8.kotlin.KotlinLambdaMergingTest.Lambda;
import com.android.tools.r8.kotlin.KotlinLambdaMergingTest.Verifier;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import org.junit.Test;

public class KotlinxMetadataExtensionsServiceTest extends TestBase {

  private void forkR8_kstyle_trivial(boolean allowAccessModification) throws Exception {
    if  (!isRunR8Jar()) {
      return;
    }
    Path working = temp.getRoot().toPath();
    Path kotlinJar =
        Paths.get(EXAMPLES_KOTLIN_RESOURCE_DIR, "JAVA_8", "lambdas_kstyle_trivial.jar")
            .toAbsolutePath();
    Path output = working.resolve("classes.dex");
    assertFalse(Files.exists(output));
    Path proguardConfiguration = temp.newFile("test.conf").toPath();
    List<String> lines = ImmutableList.of(
        "-keepattributes Signature,InnerClasses,EnclosingMethod",
        "-keep class **MainKt {",
        "  public static void main(...);",
        "}",
        "-printmapping",
        "-dontobfuscate",
        allowAccessModification ? "-allowaccessmodification" : ""
    );
    FileUtils.writeTextFile(proguardConfiguration, lines);
    ProcessResult result = ToolHelper.forkR8Jar(working,
        "--pg-conf", proguardConfiguration.toString(),
        "--lib", ToolHelper.getAndroidJar(AndroidApiLevel.O).toAbsolutePath().toString(),
        kotlinJar.toString());
    assertEquals(0, result.exitCode);
    assertThat(result.stderr, not(containsString(
        "No MetadataExtensions instances found in the classpath")));
    assertTrue(Files.exists(output));

    DexInspector inspector = new DexInspector(output);
    Verifier verifier = new Verifier(inspector);
    String pkg = "lambdas_kstyle_trivial";
    verifier.assertLambdaGroups(
        allowAccessModification ?
            new Group[]{
                kstyle("", 0, 4),
                kstyle("", 1, 8),
                kstyle("", 2, 2), // -\
                kstyle("", 2, 5), // - 3 groups different by main method
                kstyle("", 2, 4), // -/
                kstyle("", 3, 2),
                kstyle("", 22, 2)} :
            new Group[]{
                kstyle(pkg, 0, 2),
                kstyle(pkg, 1, 4),
                kstyle(pkg, 2, 5), // - 2 groups different by main method
                kstyle(pkg, 2, 4), // -/
                kstyle(pkg, 3, 2),
                kstyle(pkg, 22, 2),
                kstyle(pkg + "/inner", 0, 2),
                kstyle(pkg + "/inner", 1, 4)}
    );

    verifier.assertLambdas(
        allowAccessModification ?
            new Lambda[]{
                new Lambda(pkg, "MainKt$testStateless$6", 1) /* Banned for limited inlining */} :
            new Lambda[]{
                new Lambda(pkg, "MainKt$testStateless$6", 1), /* Banned for limited inlining */
                new Lambda(pkg, "MainKt$testStateless$8", 2),
                new Lambda(pkg + "/inner", "InnerKt$testInnerStateless$7", 2)}
    );
  }

  @Test
  public void testTrivialKs_allowAccessModification() throws Exception {
    forkR8_kstyle_trivial(true);
  }

  @Test
  public void testTrivialKs_notAllowAccessModification() throws Exception {
    forkR8_kstyle_trivial(false);
  }

}
