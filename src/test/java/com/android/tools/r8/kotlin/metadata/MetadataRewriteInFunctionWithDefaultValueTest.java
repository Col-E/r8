// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import com.android.tools.r8.utils.codeinspector.KmValueParameterSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInFunctionWithDefaultValueTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("a", "b", "c");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInFunctionWithDefaultValueTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> defaultValueLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String default_valueLibFolder = PKG_PREFIX + "/default_value_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path default_valueLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(default_valueLibFolder, "lib"))
              .compile();
      defaultValueLibJarMap.put(targetVersion, default_valueLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = defaultValueLibJarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/default_value_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".default_value_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInFunctionWithDefaultValue() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar(), ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(defaultValueLibJarMap.get(targetVersion))
            // Keep LibKt and applyMap function, along with applyMap$default
            .addKeepRules("-keep class **.LibKt { *** applyMap*(...); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspect)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/default_value_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".default_value_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector) {
    String libClassName = PKG + ".default_value_lib.LibKt";

    ClassSubject libKt = inspector.clazz(libClassName);
    assertThat(libKt, isPresentAndNotRenamed());

    MethodSubject methodSubject = libKt.uniqueMethodWithName("applyMap");
    assertThat(methodSubject, isPresentAndNotRenamed());

    methodSubject = libKt.uniqueMethodWithName("applyMap$default");
    assertThat(methodSubject, isPresentAndNotRenamed());

    KmPackageSubject kmPackage = libKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    // String applyMap(Map<String, String>, (default) String)
    KmFunctionSubject kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("applyMap");
    assertThat(kmFunction, isExtensionFunction());
    List<KmValueParameterSubject> valueParameters = kmFunction.valueParameters();
    assertEquals(2, valueParameters.size());

    KmValueParameterSubject valueParameter = valueParameters.get(0);
    assertFalse(valueParameter.declaresDefaultValue());
    assertEquals(KT_MAP, valueParameter.type().descriptor());
    List<KmTypeProjectionSubject> typeArguments = valueParameter.type().typeArguments();
    assertEquals(2, typeArguments.size());
    KmTypeSubject typeArgument = typeArguments.get(0).type();
    assertEquals(KT_STRING, typeArgument.descriptor());
    typeArgument = typeArguments.get(1).type();
    assertEquals(KT_STRING, typeArgument.descriptor());

    valueParameter = valueParameters.get(1);
    assertTrue(valueParameter.declaresDefaultValue());
    assertEquals(KT_STRING, valueParameter.type().descriptor());
  }
}