// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionFunction;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionProperty;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmFunctionSubject;
import com.android.tools.r8.utils.codeinspector.KmPackageSubject;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
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
public class MetadataRewriteInExtensionPropertyTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInExtensionPropertyTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> extLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String extLibFolder = PKG_PREFIX + "/extension_property_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path extLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(extLibFolder, "B"))
              .compile();
      extLibJarMap.put(targetVersion, extLibJar);
    }
  }

  @Test
  public void testMetadataInExtensionProperty_merged() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(extLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep the BKt extension property which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String superClassName = pkg + ".extension_property_lib.Super";
    final String bClassName = pkg + ".extension_property_lib.B";
    final String bKtClassName = pkg + ".extension_property_lib.BKt";
    compileResult.inspect(inspector -> {
      assertThat(inspector.clazz(superClassName), not(isPresent()));

      ClassSubject impl = inspector.clazz(bClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = impl.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Super")));
      KmFunctionSubject kmFunction = kmClass.kmFunctionWithUniqueName("doStuff");
      assertThat(kmFunction, isPresent());
      assertThat(kmFunction, not(isExtensionFunction()));

      ClassSubject bKt = inspector.clazz(bKtClassName);
      assertThat(bKt, isPresent());
      assertThat(bKt, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmPackageSubject kmPackage = bKt.getKmPackage();
      assertThat(kmPackage, isPresent());

      KmPropertySubject kmProperty = kmPackage.kmPropertyExtensionWithUniqueName("asI");
      assertThat(kmProperty, isExtensionProperty());
      assertNotNull(kmProperty.getterSignature());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/extension_property_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".extension_property_app.MainKt")
        .assertSuccessWithOutputLines("do stuff", "do stuff");
  }

  @Test
  public void testMetadataInExtensionProperty_renamed() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramFiles(extLibJarMap.get(targetVersion))
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep Super, but allow minification.
            .addKeepRules("-keep,allowobfuscation class **.Super")
            // Keep the BKt extension property which requires metadata
            // to be called with Kotlin syntax from other kotlin code.
            .addKeepRules("-keep class **.BKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile();
    String pkg = getClass().getPackage().getName();
    final String superClassName = pkg + ".extension_property_lib.Super";
    final String bClassName = pkg + ".extension_property_lib.B";
    final String bKtClassName = pkg + ".extension_property_lib.BKt";
    compileResult.inspect(inspector -> {
      ClassSubject sup = inspector.clazz(superClassName);
      assertThat(sup, isRenamed());

      ClassSubject impl = inspector.clazz(bClassName);
      assertThat(impl, isPresent());
      assertThat(impl, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmClassSubject kmClass = impl.getKmClass();
      assertThat(kmClass, isPresent());
      List<ClassSubject> superTypes = kmClass.getSuperTypes();
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Super")));
      assertTrue(superTypes.stream().anyMatch(
          supertype -> supertype.getFinalDescriptor().equals(sup.getFinalDescriptor())));

      ClassSubject bKt = inspector.clazz(bKtClassName);
      assertThat(bKt, isPresent());
      assertThat(bKt, not(isRenamed()));
      // API entry is kept, hence the presence of Metadata.
      KmPackageSubject kmPackage = bKt.getKmPackage();
      assertThat(kmPackage, isPresent());

      KmPropertySubject kmProperty = kmPackage.kmPropertyExtensionWithUniqueName("asI");
      assertThat(kmProperty, isExtensionProperty());
      assertNotNull(kmProperty.getterSignature());
    });

    Path libJar = compileResult.writeToZip();

    String appFolder = PKG_PREFIX + "/extension_property_app";
    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(appFolder, "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), pkg + ".extension_property_app.MainKt")
        .assertSuccessWithOutputLines("do stuff", "do stuff");
  }
}