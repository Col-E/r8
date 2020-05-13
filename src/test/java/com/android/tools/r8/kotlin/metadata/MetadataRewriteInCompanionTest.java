// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
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
public class MetadataRewriteInCompanionTest extends KotlinMetadataTestBase {
  private static final String EXPECTED =
      StringUtils.lines(
          "B.Companion::foo",
          "B.Companion::foo",
          "B.Companion::foo",
          "B.Companion::foo",
          "B.Companion::bar",
          "Hello World!");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInCompanionTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static Map<KotlinTargetVersion, Path> companionLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String companionLibFolder = PKG_PREFIX + "/companion_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path companionLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(companionLibFolder, "lib"))
              .compile();
      companionLibJarMap.put(targetVersion, companionLibJar);
    }
  }

  @Test
  public void smokeTest() throws Exception {
    Path libJar = companionLibJarMap.get(targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/companion_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".companion_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInCompanion_kept() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(companionLibJarMap.get(targetVersion))
            .addKeepKotlinMetadata()
            // Keep everything
            .addKeepRules("-keep class **.companion_lib.** { *; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            // To keep @JvmField annotation
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_INVISIBLE_ANNOTATIONS)
            // To keep ...$Companion structure
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(codeInspector -> inspect(codeInspector, true))
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/companion_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".companion_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testMetadataInCompanion_renamed() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(ToolHelper.getKotlinStdlibJar())
            .addProgramFiles(companionLibJarMap.get(targetVersion))
            .addKeepKotlinMetadata()
            // Keep the B class and its interface (which has the doStuff method).
            .addKeepRules("-keep class **.B")
            // Property in companion with @JvmField is defined in the host class, without accessors.
            .addKeepRules("-keepclassmembers class **.B { *** elt2; }")
            .addKeepRules("-keep class **.I { <methods>; }")
            // Keep getters/setters for B$Companion.(eltN|foo) which will be referenced at the app.
            .addKeepRules("-keepclassmembers class **.B$* { *** get*(...); *** set*(...); }")
            // Keep the companion instance in the B class
            .addKeepRules("-keepclassmembers class **.B { *** Companion; }")
            // Keep the class of the companion class.
            .addKeepRules("-keep class **.*$Companion")
            // No rule for Super, but will be kept and renamed.
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            // To keep @JvmField annotation
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_INVISIBLE_ANNOTATIONS)
            // To keep ...$Companion structure
            .addKeepAttributes(ProguardKeepAttributes.INNER_CLASSES)
            .addKeepAttributes(ProguardKeepAttributes.ENCLOSING_METHOD)
            .compile()
            .inspect(codeInspector -> inspect(codeInspector, false))
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(getKotlinFileInTest(PKG_PREFIX + "/companion_app", "main"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".companion_app.MainKt")
        .assertSuccessWithOutput(EXPECTED);
  }

  private void inspect(CodeInspector inspector, boolean keptAll) {
    final String superClassName = PKG + ".companion_lib.Super";
    final String bClassName = PKG + ".companion_lib.B";
    final String companionClassName = bClassName + "$Companion";

    ClassSubject sup = inspector.clazz(superClassName);
    if (keptAll) {
      assertThat(sup, isPresent());
      assertThat(sup, not(isRenamed()));
    } else {
      assertThat(sup, isRenamed());
    }

    ClassSubject impl = inspector.clazz(bClassName);
    assertThat(impl, isPresent());
    assertThat(impl, not(isRenamed()));
    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = impl.getKmClass();
    assertThat(kmClass, isPresent());
    List<ClassSubject> superTypes = kmClass.getSuperTypes();
    if (!keptAll) {
      assertTrue(superTypes.stream().noneMatch(
          supertype -> supertype.getFinalDescriptor().contains("Super")));
      assertTrue(superTypes.stream().anyMatch(
          supertype -> supertype.getFinalDescriptor().equals(sup.getFinalDescriptor())));
    }

    // The backing field for the property in the companion, with @JvmField
    FieldSubject elt2 = impl.uniqueFieldWithName("elt2");
    assertThat(elt2, isPresent());
    assertThat(elt2, not(isRenamed()));

    FieldSubject companionObject = impl.uniqueFieldWithName("Companion");
    assertThat(companionObject, isPresent());
    assertThat(companionObject, not(isRenamed()));
    assertEquals(companionObject.getFinalName(), kmClass.getCompanionObject());

    // Bridge for the property in the companion that needs a backing field.
    MethodSubject elt1Bridge = impl.uniqueMethodWithName("access$getElt1$cp");
    if (keptAll) {
      assertThat(elt1Bridge, isPresent());
      assertThat(elt1Bridge, not(isRenamed()));
    } else {
      assertThat(elt1Bridge, isRenamed());
    }

    // With @JvmField, no bridge is added.
    MethodSubject elt2Bridge = impl.uniqueMethodWithName("access$getElt2$cp");
    assertThat(elt2Bridge, not(isPresent()));

    // For B$Companion.foo, which is a simple computation, no backing field needed, hence no bridge.
    MethodSubject fooBridge = impl.uniqueMethodWithName("access$getFoo$cp");
    assertThat(fooBridge, not(isPresent()));

    ClassSubject companion = inspector.clazz(companionClassName);
    assertThat(companion, isPresent());
    assertThat(companion, not(isRenamed()));

    List<String> nestedClassDescriptors = kmClass.getNestedClassDescriptors();
    assertEquals(1, nestedClassDescriptors.size());
    assertEquals(companion.getFinalDescriptor(), nestedClassDescriptors.get(0));

    kmClass = companion.getKmClass();
    assertThat(kmClass, isPresent());

    KmPropertySubject kmProperty = kmClass.kmPropertyWithUniqueName("elt1");
    assertThat(kmProperty, isPresent());
    kmProperty = kmClass.kmPropertyWithUniqueName("elt2");
    assertThat(kmProperty, isPresent());
    kmProperty = kmClass.kmPropertyWithUniqueName("foo");
    assertThat(kmProperty, isPresent());

    MethodSubject elt1Getter = companion.uniqueMethodWithName("getElt1");
    assertThat(elt1Getter, isPresent());
    assertThat(elt1Getter, not(isRenamed()));

    // Note that there is no getter for property with @JvmField.
    MethodSubject elt2Getter = companion.uniqueMethodWithName("getElt2");
    assertThat(elt2Getter, not(isPresent()));

    MethodSubject fooGetter = companion.uniqueMethodWithName("getFoo");
    assertThat(fooGetter, isPresent());
    assertThat(fooGetter, not(isRenamed()));

    MethodSubject barSetter = companion.uniqueMethodWithName("setBar");
    assertThat(barSetter, isPresent());
    assertThat(barSetter, not(isRenamed()));
  }
}
