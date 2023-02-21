// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanTopLevelFlags;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.transformers.MethodTransformer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.time.LocalDate;
import java.util.Collection;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LocalDateEpochTest extends DesugaredLibraryTestBase {

  private final TestParameters parameters;

  private static final String EXPECTED_OUTPUT = StringUtils.lines("1970-01-01");

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withAllApiLevels()
        .build();
  }

  public LocalDateEpochTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.R))
        .addProgramClasses(DesugarLocalDate.class)
        .addProgramClassFileData(getMainClassFileData())
        .setMinApi(parameters)
        .addOptionsModification(opt -> opt.setDesugaredLibrarySpecification(getSpecification(opt)))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.R))
        .addProgramClasses(DesugarLocalDate.class)
        .addProgramClassFileData(getMainClassFileData())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addOptionsModification(opt -> opt.setDesugaredLibrarySpecification(getSpecification(opt)))
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private DesugaredLibrarySpecification getSpecification(InternalOptions options) {
    DexType date = options.dexItemFactory().createType("Ljava/time/LocalDate;");
    DexType desugarDate =
        options
            .dexItemFactory()
            .createType("L" + DescriptorUtils.getClassBinaryName(DesugarLocalDate.class) + ";");
    DexString epoch = options.dexItemFactory().createString("EPOCH");
    DexField src = options.dexItemFactory().createField(date, date, epoch);
    HumanRewritingFlags rewritingFlags =
        HumanRewritingFlags.builder(options.reporter, Origin.unknown())
            .retargetStaticField(src, src.withHolder(desugarDate, options.dexItemFactory()))
            .amendLibraryField(
                src,
                FieldAccessFlags.fromSharedAccessFlags(
                    Constants.ACC_PUBLIC | Constants.ACC_STATIC | Constants.ACC_FINAL))
            .build();
    return new HumanDesugaredLibrarySpecification(
        HumanTopLevelFlags.testing(), rewritingFlags, false);
  }

  private Collection<byte[]> getMainClassFileData() throws IOException {
    return ImmutableList.of(
        transformer(Main.class)
            .addMethodTransformer(
                new MethodTransformer() {
                  @Override
                  public void visitFieldInsn(
                      final int opcode,
                      final String owner,
                      final String name,
                      final String descriptor) {
                    if (name.equals("MIN")) {
                      super.visitFieldInsn(opcode, owner, "EPOCH", descriptor);
                    } else {
                      super.visitFieldInsn(opcode, owner, name, descriptor);
                    }
                  }
                })
            .transform());
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(LocalDate.MIN);
    }
  }

  static class DesugarLocalDate {

    public static final LocalDate EPOCH = LocalDate.of(1970, 1, 1);
  }
}
