// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.OutputMode;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.code.IgetObject;
import com.android.tools.r8.code.ReturnObject;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;
import org.junit.runner.RunWith;
import regress_77944861.SomeView;

@RunWith(VmTestRunner.class)
public class B77944861 extends TestBase {

  private static final String PRG =
      ToolHelper.EXAMPLES_BUILD_DIR + "regress_77944861" + FileUtils.JAR_EXTENSION;

  private AndroidApp runR8(AndroidApp app, Class main, Path out) throws Exception {
    R8Command command =
        ToolHelper.addProguardConfigurationConsumer(
            ToolHelper.prepareR8CommandBuilder(app),
            pgConfig -> {
              pgConfig.setPrintMapping(true);
              pgConfig.setPrintMappingFile(out.resolve(ToolHelper.DEFAULT_PROGUARD_MAP_FILE));
            })
            .addProguardConfiguration(
                ImmutableList.of(keepMainProguardConfiguration(main)),
                Origin.unknown())
            .setOutput(out, OutputMode.DexIndexed)
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
    DexCode code = initView.getMethod().getCode().asDexCode();
    checkInstructions(code, ImmutableList.of(
        IgetObject.class,
        ReturnObject.class));
    IgetObject fieldAccess = (IgetObject) code.instructions[0];
    DexField descriptionField = fieldAccess.getField();
    assertEquals(className, descriptionField.getHolder().toSourceString());

    ProcessResult artOutput = runOnArtRaw(processedApp, mainName);
    assertEquals(0, artOutput.exitCode);
    assertEquals(jvmOutput.stdout, artOutput.stdout);
  }

}
