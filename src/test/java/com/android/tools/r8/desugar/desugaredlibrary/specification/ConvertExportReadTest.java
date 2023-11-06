// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.specification;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase;
import com.android.tools.r8.desugar.desugaredlibrary.test.LibraryDesugaringSpecification;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.MultiAPILevelHumanDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.MultiAPILevelLegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineTopLevelFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MultiAPILevelMachineDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.DesugaredLibraryConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.HumanToMachineSpecificationConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.specificationconversion.LegacyToHumanSpecificationConverter;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ConvertExportReadTest extends DesugaredLibraryTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ConvertExportReadTest(TestParameters parameters) {
    assert parameters.isNoneRuntime();
  }

  @Test
  public void testMultiLevelLegacy() throws IOException {
    Assume.assumeTrue(ToolHelper.isLocalDevelopment());

    LibraryDesugaringSpecification legacySpec = LibraryDesugaringSpecification.JDK8;

    LegacyToHumanSpecificationConverter converter =
        new LegacyToHumanSpecificationConverter(Timing.empty());

    InternalOptions options = new InternalOptions();

    MultiAPILevelLegacyDesugaredLibrarySpecification spec =
        new MultiAPILevelLegacyDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(StringResource.fromFile(legacySpec.getSpecification()));

    DexApplication app = legacySpec.getAppForTesting(options, true);

    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec1 =
        converter.convertAllAPILevels(spec, app);

    Box<String> json = new Box<>();
    MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter.export(
        humanSpec1, (string, handler) -> json.set(string));
    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec2 =
        new MultiAPILevelHumanDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(StringResource.fromString(json.get(), Origin.unknown()));

    assertSpecEquals(humanSpec1, humanSpec2);

    Box<String> json2 = new Box<>();
    HumanToMachineSpecificationConverter converter2 =
        new HumanToMachineSpecificationConverter(Timing.empty());
    MultiAPILevelMachineDesugaredLibrarySpecification machineSpec1 =
        converter2.convertAllAPILevels(humanSpec2, app);
    MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter.export(
        machineSpec1, (string, handler) -> json2.set(string), options.dexItemFactory());

    MachineDesugaredLibrarySpecification machineSpecParsed =
        new MachineDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter, true, AndroidApiLevel.B.getLevel())
            .parse(StringResource.fromString(json2.get(), Origin.unknown()));
    assertFalse(machineSpecParsed.getRewriteType().isEmpty());
  }

  @Test
  public void testMultiLevelLegacyUsingMain() throws IOException {
    LibraryDesugaringSpecification legacySpec = LibraryDesugaringSpecification.JDK8;
    testMultiLevelUsingMain(legacySpec);
  }

  @Test
  public void testMultiLevelHumanUsingMain() throws IOException {
    LibraryDesugaringSpecification humanSpec = LibraryDesugaringSpecification.JDK11;
    testMultiLevelUsingMain(humanSpec);
  }

  private void testMultiLevelUsingMain(LibraryDesugaringSpecification spec) throws IOException {
    Assume.assumeTrue(ToolHelper.isLocalDevelopment());

    InternalOptions options = new InternalOptions();

    Path output = temp.newFile().toPath();

    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec =
        DesugaredLibraryConverter.convertMultiLevelAnythingToMachineSpecification(
            spec.getSpecification(),
            spec.getDesugarJdkLibs(),
            spec.getLibraryFiles(),
            output,
            options);

    MachineDesugaredLibrarySpecification machineSpecParsed =
        new MachineDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter, true, AndroidApiLevel.B.getLevel())
            .parse(StringResource.fromFile(output));
    assertFalse(machineSpecParsed.getRewriteType().isEmpty());

    if (humanSpec == null) {
      return;
    }

    // Validate the written spec is the read spec.
    Box<String> json = new Box<>();
    MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter.export(
        humanSpec, ((string, handler) -> json.set(string)));
    MultiAPILevelHumanDesugaredLibrarySpecification writtenHumanSpec =
        new MultiAPILevelHumanDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(StringResource.fromString(json.get(), Origin.unknown()));
    assertSpecEquals(humanSpec, writtenHumanSpec);

    // Validate converted machine spec is identical to the written one.
    HumanDesugaredLibrarySpecification humanSimpleSpec =
        new HumanDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter, true, AndroidApiLevel.B.getLevel())
            .parse(StringResource.fromString(json.get(), Origin.unknown()));
    HumanToMachineSpecificationConverter converter =
        new HumanToMachineSpecificationConverter(Timing.empty());
    DexApplication app = spec.getAppForTesting(options, true);
    MachineDesugaredLibrarySpecification machineSimpleSpec =
        converter.convert(humanSimpleSpec, app);

    assertSpecEquals(machineSimpleSpec, machineSpecParsed);
  }

  @Test
  public void testSingleLevel() throws IOException {
    Assume.assumeTrue(ToolHelper.isLocalDevelopment());

    LibraryDesugaringSpecification humanSpec = LibraryDesugaringSpecification.JDK11_PATH;

    InternalOptions options = new InternalOptions();

    DexApplication app = humanSpec.getAppForTesting(options, true);

    MultiAPILevelHumanDesugaredLibrarySpecification humanSpec2 =
        new MultiAPILevelHumanDesugaredLibrarySpecificationParser(
                options.dexItemFactory(), options.reporter)
            .parseMultiLevelConfiguration(StringResource.fromFile(humanSpec.getSpecification()));

    Box<String> json2 = new Box<>();
    HumanToMachineSpecificationConverter converter2 =
        new HumanToMachineSpecificationConverter(Timing.empty());
    MultiAPILevelMachineDesugaredLibrarySpecification machineSpec1 =
        converter2.convertAllAPILevels(humanSpec2, app);
    MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter.export(
        machineSpec1, (string, handler) -> json2.set(string), options.dexItemFactory());

    for (AndroidApiLevel api :
        new AndroidApiLevel[] {AndroidApiLevel.B, AndroidApiLevel.N, AndroidApiLevel.O}) {
      MachineDesugaredLibrarySpecification machineSpecParsed =
          new MachineDesugaredLibrarySpecificationParser(
                  options.dexItemFactory(), options.reporter, true, api.getLevel())
              .parse(StringResource.fromString(json2.get(), Origin.unknown()));

      HumanDesugaredLibrarySpecification humanSpecB =
          new HumanDesugaredLibrarySpecificationParser(
                  options.dexItemFactory(), options.reporter, true, api.getLevel())
              .parse(StringResource.fromFile(humanSpec.getSpecification()));
      MachineDesugaredLibrarySpecification machineSpecConverted =
          humanSpecB.toMachineSpecification(app, Timing.empty());

      assertSpecEquals(machineSpecConverted, machineSpecParsed);
    }
  }

  private void assertSpecEquals(
      MachineDesugaredLibrarySpecification spec1, MachineDesugaredLibrarySpecification spec2) {
    assertTopLevelFlagsEquals(spec1.getTopLevelFlags(), spec2.getTopLevelFlags());
    assertFlagsEquals(spec1.getRewritingFlags(), spec2.getRewritingFlags());
  }

  private void assertSpecEquals(
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec1,
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec2) {
    assertTopLevelFlagsEquals(humanSpec1.getTopLevelFlags(), humanSpec2.getTopLevelFlags());
    assertFlagMapEquals(humanSpec1.getCommonFlags(), humanSpec2.getCommonFlags());
    assertFlagMapEquals(humanSpec1.getLibraryFlags(), humanSpec2.getLibraryFlags());
    assertFlagMapEquals(humanSpec1.getProgramFlags(), humanSpec2.getProgramFlags());
  }

  private void assertFlagMapEquals(
      Map<ApiLevelRange, HumanRewritingFlags> commonFlags1,
      Map<ApiLevelRange, HumanRewritingFlags> commonFlags2) {
    assertEquals(commonFlags1.size(), commonFlags2.size());
    for (ApiLevelRange range : commonFlags1.keySet()) {
      assertTrue(commonFlags2.containsKey(range));
      assertFlagsEquals(commonFlags1.get(range), commonFlags2.get(range));
    }
  }

  private void assertFlagsEquals(
      MachineRewritingFlags rewritingFlags1, MachineRewritingFlags rewritingFlags2) {
    assertEquals(rewritingFlags1.getRewriteType(), rewritingFlags2.getRewriteType());
    assertEquals(rewritingFlags1.getMaintainType(), rewritingFlags2.getMaintainType());
    assertEquals(
        rewritingFlags1.getRewriteDerivedTypeOnly(), rewritingFlags2.getRewriteDerivedTypeOnly());
    assertEquals(
        rewritingFlags1.getStaticFieldRetarget(), rewritingFlags2.getStaticFieldRetarget());
    assertEquals(rewritingFlags1.getCovariantRetarget(), rewritingFlags2.getCovariantRetarget());
    assertEquals(rewritingFlags1.getStaticRetarget(), rewritingFlags2.getStaticRetarget());
    assertEquals(
        rewritingFlags1.getNonEmulatedVirtualRetarget(),
        rewritingFlags2.getNonEmulatedVirtualRetarget());
    assertEquals(
        rewritingFlags1.getEmulatedVirtualRetarget(), rewritingFlags2.getEmulatedVirtualRetarget());
    assertEquals(
        rewritingFlags1.getEmulatedVirtualRetargetThroughEmulatedInterface(),
        rewritingFlags2.getEmulatedVirtualRetargetThroughEmulatedInterface());
    assertEquals(
        rewritingFlags1.getApiGenericConversion().keySet(),
        rewritingFlags2.getApiGenericConversion().keySet());
    rewritingFlags1
        .getApiGenericConversion()
        .keySet()
        .forEach(
            k ->
                assertArrayEquals(
                    rewritingFlags1.getApiGenericConversion().get(k),
                    rewritingFlags2.getApiGenericConversion().get(k)));
    assertEquals(
        rewritingFlags1.getEmulatedInterfaces().keySet(),
        rewritingFlags2.getEmulatedInterfaces().keySet());
    assertEquals(rewritingFlags1.getWrappers().keySet(), rewritingFlags2.getWrappers().keySet());
    assertEquals(rewritingFlags1.getLegacyBackport(), rewritingFlags2.getLegacyBackport());
    assertEquals(rewritingFlags1.getDontRetarget(), rewritingFlags2.getDontRetarget());
    assertEquals(rewritingFlags1.getCustomConversions(), rewritingFlags2.getCustomConversions());
    assertEquals(rewritingFlags1.getAmendLibraryMethod(), rewritingFlags2.getAmendLibraryMethod());
    assertEquals(rewritingFlags1.getAmendLibraryField(), rewritingFlags2.getAmendLibraryField());
  }

  private void assertFlagsEquals(
      HumanRewritingFlags humanRewritingFlags1, HumanRewritingFlags humanRewritingFlags2) {
    assertEquals(humanRewritingFlags1.getRewritePrefix(), humanRewritingFlags2.getRewritePrefix());
    assertEquals(
        humanRewritingFlags1.getRewriteDerivedPrefix(),
        humanRewritingFlags2.getRewriteDerivedPrefix());

    assertEquals(
        humanRewritingFlags1.getLegacyBackport(), humanRewritingFlags2.getLegacyBackport());
    assertEquals(
        humanRewritingFlags1.getCustomConversions(), humanRewritingFlags2.getCustomConversions());
    assertEquals(
        humanRewritingFlags1.getEmulatedInterfaces(), humanRewritingFlags2.getEmulatedInterfaces());
    assertEquals(
        humanRewritingFlags1.getRetargetMethodToType(),
        humanRewritingFlags2.getRetargetMethodToType());

    assertEquals(humanRewritingFlags1.getDontRetarget(), humanRewritingFlags2.getDontRetarget());
    assertEquals(
        humanRewritingFlags1.getWrapperConversions(), humanRewritingFlags2.getWrapperConversions());

    assertEquals(
        humanRewritingFlags1.getAmendLibraryMethod(), humanRewritingFlags2.getAmendLibraryMethod());
  }

  private void assertTopLevelFlagsEquals(
      MachineTopLevelFlags topLevelFlags1, MachineTopLevelFlags topLevelFlags2) {
    String kr1 = String.join("\n", topLevelFlags1.getExtraKeepRules());
    String kr2 = String.join("\n", topLevelFlags2.getExtraKeepRules());
    assertEquals(kr1, kr2);
    assertEquals(topLevelFlags1.getIdentifier(), topLevelFlags2.getIdentifier());
    assertEquals(
        topLevelFlags1.getRequiredCompilationApiLevel().getLevel(),
        topLevelFlags2.getRequiredCompilationApiLevel().getLevel());
    assertEquals(
        topLevelFlags1.getSynthesizedLibraryClassesPackagePrefix(),
        topLevelFlags2.getSynthesizedLibraryClassesPackagePrefix());
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
