// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming.overloadaggressively;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.CompatProguardCommandBuilder;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.ToolHelper.DexVm.Kind;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OverloadAggressivelyTest extends TestBase {
  private final DexVm dexVm;
  private final boolean overloadaggressively;

  public OverloadAggressivelyTest(DexVm dexVm, boolean overloadaggressively) {
    this.dexVm = dexVm;
    this.overloadaggressively = overloadaggressively;
  }

  @Parameters(name = "vm: {0}, overloadaggressively: {1}")
  public static Collection<Object[]> data() {
    List<Object[]> testCases = new ArrayList<>();
    for (DexVm version : DexVm.values()) {
      if (version.getKind() == Kind.HOST) {
        testCases.add(new Object[]{version, true});
        testCases.add(new Object[]{version, false});
      }
    }
    return testCases;
  }

  @Test
  public void overloadAggressivelyTest() throws Exception {
    Assume.assumeTrue(ToolHelper.artSupported());
    byte[][] classes = {
        ToolHelper.getClassAsBytes(Main.class),
        ToolHelper.getClassAsBytes(A.class),
        ToolHelper.getClassAsBytes(B.class)
    };
    AndroidApp originalApp = buildAndroidApp(classes);
    Path out = temp.getRoot().toPath();
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
            ToolHelper.prepareR8CommandBuilder(originalApp),
            pgConfig -> {
              pgConfig.setPrintMapping(true);
              pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
            })
        .addProguardConfiguration(
            ImmutableList.copyOf(Iterables.concat(ImmutableList.of(
                keepMainProguardConfiguration(Main.class),
                overloadaggressively ? "-overloadaggressively" : ""),
                CompatProguardCommandBuilder.REFLECTIONS)),
            Origin.unknown())
        .setOutput(out, OutputMode.DexIndexed)
        .build();
    AndroidApp processedApp = ToolHelper.runR8(command);

    DexInspector dexInspector = new DexInspector(
        out.resolve(ToolHelper.DEFAULT_DEX_FILENAME),
        out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE).toString());
    ClassSubject a = dexInspector.clazz(A.class.getCanonicalName());
    DexEncodedField f1 = a.field("int", "f1").getField();
    assertNotNull(f1);
    DexEncodedField f2 = a.field("java.lang.Object", "f2").getField();
    assertNotNull(f2);
    assertEquals(overloadaggressively, f1.field.name == f2.field.name);
    DexEncodedField f3 = a.field(B.class.getCanonicalName(), "f3").getField();
    assertNotNull(f3);
    assertEquals(overloadaggressively, f1.field.name == f3.field.name);
    assertEquals(overloadaggressively, f2.field.name == f3.field.name);

    ProcessResult javaOutput = runOnJava(Main.class.getCanonicalName(), classes);
    ProcessResult artOutput = runOnArtRaw(processedApp, Main.class.getCanonicalName(), dexVm);
    if (overloadaggressively) {
      assertNotEquals(0, artOutput.exitCode);
      assertTrue(artOutput.stderr.contains("ClassCastException"));
    } else {
      assertEquals(0, javaOutput.exitCode);
      assertEquals(0, artOutput.exitCode);
      assertEquals(javaOutput.stdout.trim(), artOutput.stdout.trim());
      // ART may dump its own debugging info through stderr.
      // assertEquals(javaOutput.stderr.trim(), artOutput.stderr.trim());
    }
  }
}
