// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.conversiontests;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.L8Command;
import com.android.tools.r8.OutputMode;
import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestDiagnosticMessagesImpl;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConversionsPresentTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public ConversionsPresentTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testConversionsDex() throws Exception {
    TestDiagnosticMessagesImpl diagnosticsHandler = new TestDiagnosticMessagesImpl();
    Path desugaredLib = temp.newFolder().toPath().resolve("conversion_dex.zip");
    L8Command.Builder l8Builder =
        L8Command.builder(diagnosticsHandler)
            .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
            .addProgramFiles(ToolHelper.DESUGAR_LIB_CONVERSIONS)
            .addDesugaredLibraryConfiguration(
                StringResource.fromFile(ToolHelper.DESUGAR_LIB_JSON_FOR_TESTING))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setOutput(desugaredLib, OutputMode.DexIndexed);
    ToolHelper.runL8(l8Builder.build(), x -> {});
    this.checkConversionGeneratedDex(new CodeInspector(desugaredLib));
  }

  private void checkConversionGeneratedDex(CodeInspector inspector) {
    List<FoundClassSubject> conversionsClasses =
        inspector.allClasses().stream()
            .filter(c -> c.getOriginalName().contains("Conversions"))
            .collect(Collectors.toList());
    if (parameters.getApiLevel().isLessThan(AndroidApiLevel.N)) {
      assertEquals(5, conversionsClasses.size());
      assertTrue(inspector.clazz("j$.util.OptionalConversions").isPresent());
      assertTrue(inspector.clazz("j$.time.TimeConversions").isPresent());
      assertTrue(inspector.clazz("j$.util.LongSummaryStatisticsConversions").isPresent());
      assertTrue(inspector.clazz("j$.util.IntSummaryStatisticsConversions").isPresent());
      assertTrue(inspector.clazz("j$.util.DoubleSummaryStatisticsConversions").isPresent());
    } else if (parameters.getApiLevel().isLessThan(AndroidApiLevel.O)) {
      assertEquals(1, conversionsClasses.size());
      assertTrue(inspector.clazz("j$.time.TimeConversions").isPresent());
    } else {
      assertEquals(0, inspector.allClasses().size());
    }
  }
}
