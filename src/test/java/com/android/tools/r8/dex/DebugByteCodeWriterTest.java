// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static com.android.tools.r8.TestBase.getTestParameters;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexDebugInfo.EventBasedDebugInfo;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.ObjectToOffsetMapping;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.synthesis.SyntheticItems.GlobalSyntheticsStrategy;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.Timing;
import java.util.Collections;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DebugByteCodeWriterTest {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public DebugByteCodeWriterTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private ObjectToOffsetMapping emptyObjectTObjectMapping() {
    AppView<AppInfo> appView =
        AppView.createForD8(
            AppInfo.createInitialAppInfo(
                DexApplication.builder(
                        new InternalOptions(new DexItemFactory(), new Reporter()), null)
                    .build(),
                GlobalSyntheticsStrategy.forNonSynthesizing()));
    return new ObjectToOffsetMapping(
        appView,
        null,
        new LensCodeRewriterUtils(appView),
        Collections.emptyList(),
        Collections.emptyMap(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        Collections.emptyList(),
        0,
        Timing.empty());
  }

  @Test
  public void testEmptyDebugInfo() {
    DexDebugInfo debugInfo =
        new EventBasedDebugInfo(1, DexString.EMPTY_ARRAY, DexDebugEvent.EMPTY_ARRAY);
    DebugBytecodeWriter writer =
        new DebugBytecodeWriter(
            DexDebugInfo.convertToWritable(debugInfo),
            emptyObjectTObjectMapping(),
            GraphLens.getIdentityLens());
    Assert.assertEquals(3, writer.generate().length);
  }
}
