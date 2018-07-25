// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b69825683;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ClassFileConsumer;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.google.common.collect.ImmutableList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress69825683Test extends TestBase {
  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public Regress69825683Test(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void outerConstructsInner() throws Exception {
    Class mainClass = com.android.tools.r8.regress.b69825683.outerconstructsinner.Outer.class;
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestPackage(mainClass.getPackage()));
    builder.addProguardConfiguration(ImmutableList.of(
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontobfuscate"),
        Origin.unknown());
    if (backend == Backend.DEX) {
      builder
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    } else {
      assert backend == Backend.CF;
      builder
          .setProgramConsumer(ClassFileConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    AndroidApp app = ToolHelper.runR8(builder.build(), o -> o.enableClassInlining = false);
    CodeInspector inspector = new CodeInspector(app, o -> o.enableCfFrontend = true);
    List<FoundClassSubject> classes = inspector.allClasses();

    // Check that the synthetic class is still present.
    assertEquals(3, classes.size());
    assertEquals(1,
        classes.stream()
            .map(FoundClassSubject::getOriginalName)
            .filter(name  -> name.endsWith("$1"))
            .count());

    // Run code to check that the constructor with synthetic class as argument is present.
    Class innerClass =
        com.android.tools.r8.regress.b69825683.outerconstructsinner.Outer.Inner.class;
    String innerName = innerClass.getCanonicalName();
    int index = innerName.lastIndexOf('.');
    innerName = innerName.substring(0, index) + "$" + innerName.substring(index + 1);
    assertTrue(
        (backend == Backend.DEX ? runOnArt(app, mainClass) : runOnJava(app, mainClass))
            .startsWith(innerName));
  }

  @Test
  public void innerConstructsOuter() throws Exception {
    Class mainClass = com.android.tools.r8.regress.b69825683.innerconstructsouter.Outer.class;
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(ToolHelper.getClassFilesForTestPackage(mainClass.getPackage()));
    builder.addProguardConfiguration(ImmutableList.of(
        "-keep class " + mainClass.getCanonicalName() + " {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-dontobfuscate"),
        Origin.unknown());
    if (backend == Backend.DEX) {
      builder
          .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getDefaultAndroidJar());
    } else {
      assert backend == Backend.CF;
      builder
          .setProgramConsumer(ClassFileConsumer.emptyConsumer())
          .addLibraryFiles(ToolHelper.getJava8RuntimeJar());
    }
    AndroidApp app = ToolHelper.runR8(builder.build(), o -> o.enableClassInlining = false);
    CodeInspector inspector = new CodeInspector(app, o -> o.enableCfFrontend = true);
    List<FoundClassSubject> classes = inspector.allClasses();

    // Check that the synthetic class is still present.
    assertEquals(3, classes.size());
    assertEquals(1,
        classes.stream()
            .map(FoundClassSubject::getOriginalName)
            .filter(name  -> name.endsWith("$1"))
            .count());

    // Run code to check that the constructor with synthetic class as argument is present.
    assertTrue(
        (backend == Backend.DEX ? runOnArt(app, mainClass) : runOnJava(app, mainClass))
            .startsWith(mainClass.getCanonicalName()));
  }
}
