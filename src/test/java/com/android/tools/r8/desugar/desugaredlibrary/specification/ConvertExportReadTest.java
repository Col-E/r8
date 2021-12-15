// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.specification;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.LegacyToHumanSpecificationConverter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import java.io.IOException;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConvertExportReadTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ConvertExportReadTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testMultiLevel() throws IOException {
    Assume.assumeTrue(ToolHelper.isLocalDevelopment());

    LegacyToHumanSpecificationConverter converter = new LegacyToHumanSpecificationConverter();

    InternalOptions options = new InternalOptions();

    MultiAPILevelLegacyDesugaredLibrarySpecification spec =
        new MultiAPILevelLegacyDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(
                StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()));

    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec1 =
        converter.convertAllAPILevels(spec, ToolHelper.getAndroidJar(31), options);

    Box<String> json = new Box<>();
    MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter.export(
        humanSpec1, (string, handler) -> json.set(string));
    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec2 =
        new MultiAPILevelHumanDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(StringResource.fromString(json.get(), Origin.unknown()));

    assertSpecEquals(humanSpec1, humanSpec2);
  }

  private void assertSpecEquals(
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec1,
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec2) {
    assertTopLevelFlagsEquals(humanSpec1.getTopLevelFlags(), humanSpec2.getTopLevelFlags());
    assertFlagMapEquals(
        humanSpec1.getCommonFlagsForTesting(), humanSpec2.getCommonFlagsForTesting());
    assertFlagMapEquals(
        humanSpec1.getLibraryFlagsForTesting(), humanSpec2.getLibraryFlagsForTesting());
    assertFlagMapEquals(
        humanSpec1.getProgramFlagsForTesting(), humanSpec2.getProgramFlagsForTesting());
  }

  private void assertFlagMapEquals(
      Map<Integer, HumanRewritingFlags> commonFlags1,
      Map<Integer, HumanRewritingFlags> commonFlags2) {
    assertEquals(commonFlags1.size(), commonFlags2.size());
    for (int integer : commonFlags1.keySet()) {
      assertTrue(commonFlags2.containsKey(integer));
      assertFlagsEquals(commonFlags1.get(integer), commonFlags2.get(integer));
    }
  }

  private void assertFlagsEquals(
      HumanRewritingFlags humanRewritingFlags1, HumanRewritingFlags humanRewritingFlags2) {
    assertEquals(humanRewritingFlags1.getRewritePrefix(), humanRewritingFlags2.getRewritePrefix());
    assertEquals(
        humanRewritingFlags1.getBackportCoreLibraryMember(),
        humanRewritingFlags2.getBackportCoreLibraryMember());
    assertEquals(
        humanRewritingFlags1.getCustomConversions(), humanRewritingFlags2.getCustomConversions());
    assertEquals(
        humanRewritingFlags1.getEmulateLibraryInterface(),
        humanRewritingFlags2.getEmulateLibraryInterface());
    assertEquals(
        humanRewritingFlags1.getRetargetCoreLibMember(),
        humanRewritingFlags2.getRetargetCoreLibMember());

    assertEquals(
        humanRewritingFlags1.getDontRetargetLibMember(),
        humanRewritingFlags2.getDontRetargetLibMember());
    assertEquals(
        humanRewritingFlags1.getDontRewriteInvocation(),
        humanRewritingFlags2.getDontRewriteInvocation());
    assertEquals(
        humanRewritingFlags1.getWrapperConversions(), humanRewritingFlags2.getWrapperConversions());
  }

  private void assertTopLevelFlagsEquals(
      HumanTopLevelFlags topLevelFlags1, HumanTopLevelFlags topLevelFlags2) {
    assertEquals(topLevelFlags1.getExtraKeepRules(), topLevelFlags2.getExtraKeepRules());
    assertEquals(topLevelFlags1.getIdentifier(), topLevelFlags2.getIdentifier());
    assertEquals(
        topLevelFlags1.getRequiredCompilationAPILevel().getLevel(),
        topLevelFlags2.getRequiredCompilationAPILevel().getLevel());
    assertEquals(
        topLevelFlags1.getSynthesizedLibraryClassesPackagePrefix(),
        topLevelFlags2.getSynthesizedLibraryClassesPackagePrefix());
  }
}
