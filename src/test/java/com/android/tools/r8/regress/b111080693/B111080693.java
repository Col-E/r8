// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b111080693;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.regress.b111080693.a.Observable;
import com.android.tools.r8.regress.b111080693.b.RecyclerView;
import com.android.tools.r8.utils.AndroidApp;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(VmTestRunner.class)
public class B111080693 extends TestBase {
  @Test
  public void test() throws Exception {
    R8Command.Builder builder = R8Command.builder();
    builder.addProgramFiles(
        ToolHelper.getClassFilesForTestPackage(Observable.class.getPackage()));
    builder.addProgramFiles(
        ToolHelper.getClassFilesForTestPackage(RecyclerView.class.getPackage()));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestMain.class));
    builder.addProgramFiles(ToolHelper.getClassFileForTestClass(TestMain.TestAdapter.class));
    builder.setProgramConsumer(DexIndexedConsumer.emptyConsumer());
    builder.setMinApiLevel(ToolHelper.getMinApiLevelForDexVm().getLevel());
    String config = keepMainProguardConfiguration(TestMain.class);
    builder.addProguardConfiguration(
        ImmutableList.of(config,
            "-keepattributes Signature, InnerClasses, EnclosingMethod, *Annotation*"),
        Origin.unknown());
    AndroidApp app = ToolHelper.runR8(builder.build());
    ProcessResult result = runOnArtRaw(app, TestMain.class);
    assertEquals(0, result.exitCode);
    assertThat(result.stderr, not(containsString("IllegalAccessError")));
  }
}
