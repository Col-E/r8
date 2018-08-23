// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import regress_77944861.SomeView;

@RunWith(Parameterized.class)
public class B77944861 extends TestBase {

  private static final String PRG =
      ToolHelper.EXAMPLES_BUILD_DIR + "regress_77944861" + FileUtils.JAR_EXTENSION;

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public B77944861(Backend backend) {
    this.backend = backend;
  }

  private AndroidApp runR8(AndroidApp app, Class main, Path out) throws Exception {
    assert backend == Backend.DEX || backend == Backend.CF;
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
                ToolHelper.prepareR8CommandBuilder(app),
                pgConfig -> {
                  pgConfig.setPrintMapping(true);
                  pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
                })
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(main)), Origin.unknown())
            .setOutput(out, backend == Backend.DEX ? OutputMode.DexIndexed : OutputMode.ClassFile)
            .addLibraryFiles(TestBase.runtimeJar(backend))
            .build();
    return ToolHelper.runR8(command, o -> {
      o.enableMinification = false;
      o.enableInlining = false;
      o.enableTreeShaking = false;
    });
  }

  @Test
  public void test() throws Exception {
    Path out = temp.getRoot().toPath();
    Path jarPath = Paths.get(PRG);
    String mainName = SomeView.class.getCanonicalName();
    ProcessResult jvmOutput = ToolHelper.runJava(ImmutableList.of(jarPath), mainName);
    assertEquals(0, jvmOutput.exitCode);
    AndroidApp processedApp = runR8(readJar(jarPath), SomeView.class, out);
    CodeInspector codeInspector = new CodeInspector(processedApp);
    ClassSubject view = codeInspector.clazz("regress_77944861.SomeView");
    assertThat(view, isPresent());
    String className = "regress_77944861.inner.TopLevelPolicy$MobileIconState";
    MethodSubject initView = view.method("java.lang.String", "get", ImmutableList.of(className));
    assertThat(initView, isPresent());
    Iterator<InstructionSubject> iterator = initView.iterateInstructions();
    InstructionSubject instruction;
    do {
      assertTrue(iterator.hasNext());
      instruction = iterator.next();
    } while (!instruction.isInstanceGet());

    assertEquals(className, ((FieldAccessInstructionSubject) instruction).holder().toString());

    do {
      assertTrue(iterator.hasNext());
      instruction = iterator.next();
    } while (!instruction.isReturnObject());

    ProcessResult output;
    if (backend == Backend.DEX) {
      output = runOnArtRaw(processedApp, mainName);
    } else {
      assert backend == Backend.CF;
      output = runOnJavaRaw(processedApp, mainName, Collections.emptyList());
    }
    assertEquals(0, output.exitCode);
    assertEquals(jvmOutput.stdout, output.stdout);
  }

}
