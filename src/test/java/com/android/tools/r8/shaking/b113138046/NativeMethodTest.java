// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.b113138046;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;

public class NativeMethodTest extends TestBase {

  private void test(List<String> config) throws Exception {
    R8Command.Builder builder = R8Command.builder();
    List<Path> classes = ToolHelper.getClassFilesForTestDirectory(
        ToolHelper.getPackageDirectoryForTestPackage(Outer.class.getPackage()),
        p -> !p.getFileName().toString().startsWith(this.getClass().getSimpleName()));
    builder.addProgramFiles(classes);
    builder.addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    builder.addProguardConfiguration(config, Origin.unknown());
    Path appOut = temp.newFile("out.zip").toPath();
    builder.setOutput(appOut, OutputMode.DexIndexed);
    AndroidApp processedApp = ToolHelper.runR8(builder.build(), options -> {
      options.enableInlining = false;
    });
    Path oatOut = temp.newFile("out.oat").toPath();
    ToolHelper.runDex2Oat(appOut, oatOut);

    CodeInspector inspector = new CodeInspector(processedApp);
    boolean innerFound = false;
    for (ClassSubject clazz : inspector.allClasses()) {
      innerFound = clazz.getOriginalName().endsWith("Inner");
      if (!innerFound) {
        continue;
      }
      MethodSubject nativeFoo = clazz.method("void", "foo",
          ImmutableList.of(Handler.class.getCanonicalName()));
      assertThat(nativeFoo, isPresent());
      assertThat(nativeFoo, not(isRenamed()));
      DexEncodedMethod method = nativeFoo.getMethod();
      assertTrue(method.accessFlags.isNative());
      assertNull(method.getCode());
      break;
    }
    assertTrue(innerFound);
  }

  @Test
  public void test_keep_OnEvent() throws Exception {
    List<String> config = ImmutableList.of(
        "-keepclasseswithmembers,allowshrinking class * {",
        "  native <methods>;",
        "}",
        "-keepclassmembers,includedescriptorclasses class * {",
        "  native <methods>;",
        "}",
        "-keep class " + Outer.class.getCanonicalName() + " {",
        "  onEvent(...);",
        "}",
        "-printmapping",
        "-keepattributes InnerClasses,EnclosingMethod,Signature",
        "-allowaccessmodification");
    test(config);
  }

  @Test
  public void test_keep_annotation() throws Exception {
    List<String> config = ImmutableList.of(
        "-keepclasseswithmembers,allowshrinking class * {",
        "  native <methods>;",
        "}",
        "-keepclassmembers,includedescriptorclasses class * {",
        "  native <methods>;",
        "}",
        "-keep,allowobfuscation @interface **.Keep",
        "-keep @**.Keep class *",
        "-keepclassmembers class * {",
        "  @**.Keep <fields>;",
        "  @**.Keep <methods>;",
        "}",
        "-printmapping",
        "-keepattributes InnerClasses,EnclosingMethod,Signature",
        "-allowaccessmodification");
    test(config);
  }
}
