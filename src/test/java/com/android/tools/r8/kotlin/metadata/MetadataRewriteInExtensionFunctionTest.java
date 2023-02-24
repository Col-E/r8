// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ZipUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeProjectionSubject;
import com.android.tools.r8.utils.codeinspector.KmTypeSubject;
import com.android.tools.r8.utils.codeinspector.KmValueParameterSubject;
import com.android.tools.r8.utils.codeinspector.Matchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInExtensionFunctionTest extends KotlinMetadataTestBase {
  private static final String EXPECTED = StringUtils.lines("do stuff", "do stuff", "do stuff");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}, {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(),
        getKotlinTestParameters()
            .withCompilersStartingFromIncluding(MIN_SUPPORTED_VERSION)
            .withAllTargetVersions()
            .build());
  }

  public MetadataRewriteInExtensionFunctionTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer extLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/extension_function_lib", "B"));

  @Test
  public void smokeTest() throws Exception {
    Path libJar = extLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInExtensionFunction_merged_compat() throws Exception {
    testMetadataInExtensionFunction_merged(false);
  }

  @Test
  public void testMetadataInExtensionFunction_merged_full() throws Exception {
    testMetadataInExtensionFunction_merged(true);
  }

  public void testMetadataInExtensionFunction_merged(boolean full) throws Exception {
    Path libJar =
        (full ? testForR8(parameters.getBackend()) : testForR8Compat(parameters.getBackend()))
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(extLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep the BKt extension function which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(inspector -> inspectMerged(inspector, full))
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile(full || kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (full || kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectMerged(CodeInspector inspector, boolean full) {
    String superClassName = PKG + ".extension_function_lib.Super";
    String bClassName = PKG + ".extension_function_lib.B";

    assertThat(inspector.clazz(superClassName), Matchers.notIf(isPresent(), full));

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Super")));

    inspectExtensions(inspector);
  }

  @Test
  public void testMetadataInExtensionFunction_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(extLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Super")
            // Keep the BKt extension function which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(this::inspectRenamed)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInExtensionFunction_renamedKotlinSources() throws Exception {
    assumeTrue(kotlinc.getCompilerVersion().isGreaterThanOrEqualTo(KOTLINC_1_4_20));
    R8TestCompileResult r8LibraryResult =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(extLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Super")
            // Keep the BKt extension function which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep,allowobfuscation class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .addKeepAttributes(ProguardKeepAttributes.SIGNATURE)
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile();
    Path kotlinSourcePath = getKotlinFileInTest(PKG_PREFIX + "/extension_function_app", "main");

    String kotlinSource = FileUtils.readTextFile(kotlinSourcePath, StandardCharsets.UTF_8);

    CodeInspector inspector = r8LibraryResult.inspector();

    ClassSubject clazz = inspector.clazz(PKG + ".extension_function_lib.BKt");
    assertThat(clazz, isPresentAndRenamed());

    // Rewrite the source kotlin files that reference the four extension methods into their renamed
    // name by changing the import statement and the actual call.
    String[] methodNames = new String[] {"extension", "csHash", "longArrayHash", "myApply"};
    for (String methodName : methodNames) {
      MethodSubject method = clazz.uniqueMethodWithOriginalName(methodName);
      assertThat(method, isPresentAndRenamed());
      String finalMethodName = method.getFinalName();
      kotlinSource =
          kotlinSource.replace(
              "import com.android.tools.r8.kotlin.metadata.extension_function_lib." + methodName,
              "import "
                  + DescriptorUtils.getPackageNameFromTypeName(clazz.getFinalName())
                  + "."
                  + finalMethodName);
      kotlinSource = kotlinSource.replace(")." + methodName, ")." + finalMethodName);
    }

    Path newSource = temp.newFolder().toPath().resolve("main.kt");
    Files.write(newSource, kotlinSource.getBytes(StandardCharsets.UTF_8));

    Path libJar = r8LibraryResult.writeToZip();
    Path tempUnzipPath = temp.newFolder().toPath();
    List<String> kotlinModuleFiles = new ArrayList<>();
    ZipUtils.unzip(
        libJar,
        tempUnzipPath,
        f -> {
          if (f.getName().endsWith(".kotlin_module")) {
            kotlinModuleFiles.add(f.getName());
          }
          return false;
        });
    assertEquals(Collections.singletonList("META-INF/main.kotlin_module"), kotlinModuleFiles);
    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(newSource)
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".extension_function_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspectRenamed(CodeInspector inspector) {
    String superClassName = PKG + ".extension_function_lib.Super";
    String bClassName = PKG + ".extension_function_lib.B";

    ClassSubject sup = inspector.clazz(superClassName);
    assertThat(sup, isPresentAndRenamed());

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    assertTrue(superTypes.stream().noneMatch(
        supertype -> supertype.getFinalDescriptor().contains("Super")));
    assertTrue(superTypes.stream().anyMatch(
        supertype -> supertype.getFinalDescriptor().equals(sup.getFinalDescriptor())));

    inspectExtensions(inspector);
  }

  private void inspectExtensions(CodeInspector inspector) {
    String bClassName = PKG + ".extension_function_lib.B";
    String bKtClassName = PKG + ".extension_function_lib.BKt";

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresent());

    ClassSubject bKt = inspector.clazz(bKtClassName);
    assertThat(bKt, isPresentAndNotRenamed());
    // API entry is kept, hence the presence of Metadata.
    KmPackageSubject kmPackage = bKt.getKmPackage();
    assertThat(kmPackage, isPresent());

    KmFunctionSubject kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("extension");
    assertThat(kmFunction, isExtensionFunction());
    KmTypeSubject kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(impl.getFinalDescriptor(), kmTypeSubject.descriptor());

    kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("csHash");
    assertThat(kmFunction, isExtensionFunction());
    kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(KT_CHAR_SEQUENCE, kmTypeSubject.descriptor());
    kmTypeSubject = kmFunction.returnType();
    assertEquals(KT_LONG, kmTypeSubject.descriptor());

    kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("longArrayHash");
    assertThat(kmFunction, isExtensionFunction());
    kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(KT_LONG_ARRAY, kmTypeSubject.descriptor());
    kmTypeSubject = kmFunction.returnType();
    assertEquals(KT_LONG, kmTypeSubject.descriptor());

    // fun B.myApply(apply: B.() -> Unit): Unit
    // https://github.com/JetBrains/kotlin/blob/master/spec-docs/function-types.md#extension-functions
    kmFunction = kmPackage.kmFunctionExtensionWithUniqueName("myApply");
    assertThat(kmFunction, isExtensionFunction());
    kmTypeSubject = kmFunction.receiverParameterType();
    assertEquals(impl.getFinalDescriptor(), kmTypeSubject.descriptor());

    List<KmValueParameterSubject> valueParameters = kmFunction.valueParameters();
    assertEquals(1, valueParameters.size());

    KmValueParameterSubject valueParameter = valueParameters.get(0);
    assertEquals(KT_FUNCTION1, valueParameter.type().descriptor());
    List<KmTypeProjectionSubject> typeArguments = valueParameter.type().typeArguments();
    assertEquals(2, typeArguments.size());
    KmTypeSubject typeArgument = typeArguments.get(0).type();
    assertEquals(impl.getFinalDescriptor(), typeArgument.descriptor());
    typeArgument = typeArguments.get(1).type();
    assertEquals(KT_UNIT, typeArgument.descriptor());
  }
}
