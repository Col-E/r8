// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_4_20;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.KOTLINC_1_5_0;
import static com.android.tools.r8.KotlinCompilerTool.KotlinCompilerVersion.MIN_SUPPORTED_VERSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionProperty;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.TestCase.assertNull;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInPropertyTest extends KotlinMetadataTestBase {
  private static final String EXPECTED_GETTER = StringUtils.lines("true", "false", "Hey Jude");

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

  public MetadataRewriteInPropertyTest(
      TestParameters parameters, KotlinTestParameters kotlinParameters) {
    super(kotlinParameters);
    this.parameters = parameters;
  }

  private static final KotlinCompileMemoizer propertyTypeLibJarMap =
      getCompileMemoizer(getKotlinFileInTest(PKG_PREFIX + "/fragile_property_lib", "lib"));

  @Test
  public void smokeTest_getterApp() throws Exception {
    Path libJar = propertyTypeLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(PKG_PREFIX + "/fragile_property_only_getter", "getter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".fragile_property_only_getter.Getter_userKt")
        .assertSuccessWithOutput(EXPECTED_GETTER);
  }

  @Test
  public void testMetadataInProperty_getterOnly() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(propertyTypeLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep property getters
            .addKeepRules("-keep class **.Person { <init>(...); }")
            .addKeepRules("-keepclassmembers class **.Person { *** get*(); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectGetterOnly)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(PKG_PREFIX + "/fragile_property_only_getter", "getter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".fragile_property_only_getter.Getter_userKt")
        .assertSuccessWithOutput(EXPECTED_GETTER);
  }

  private void inspectGetterOnly(CodeInspector inspector) {
    String personClassName = PKG + ".fragile_property_lib.Person";
    ClassSubject person = inspector.clazz(personClassName);
    assertThat(person, isPresentAndNotRenamed());

    FieldSubject backingField = person.uniqueFieldWithOriginalName("name");
    assertThat(backingField, isPresentAndRenamed());
    MethodSubject getterForName = person.uniqueMethodWithOriginalName("getName");
    assertThat(getterForName, isPresentAndNotRenamed());
    MethodSubject setterForName = person.uniqueMethodWithOriginalName("setName");
    assertThat(setterForName, not(isPresent()));

    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = person.getKmClass();
    assertThat(kmClass, isPresent());

    KmPropertySubject name = kmClass.kmPropertyWithUniqueName("name");
    assertThat(name, isPresent());
    assertThat(name, not(isExtensionProperty()));
    // Property name is not renamed, due to the kept getter.
    assertEquals("name", name.name());
    assertNotNull(name.fieldSignature());
    assertEquals(backingField.getJvmFieldSignatureAsString(), name.fieldSignature().asString());
    assertNotNull(name.getterSignature());
    assertEquals(getterForName.getJvmMethodSignatureAsString(), name.getterSignature().asString());
    assertEquals(name.setterSignature().asString(), "setName(Ljava/lang/String;)V");

    KmPropertySubject familyName = kmClass.kmPropertyWithUniqueName("familyName");
    assertThat(familyName, isPresent());
    assertThat(familyName, not(isExtensionProperty()));
    // No backing field for property `familyName`
    assertNull(familyName.fieldSignature());
    assertEquals(familyName.getterSignature().asString(), "getFamilyName()Ljava/lang/String;");
    assertNull(familyName.setterSignature());

    KmPropertySubject age = kmClass.kmPropertyWithUniqueName("age");
    assertThat(age, isPresent());
    assertThat(age, not(isExtensionProperty()));
    assertEquals(
        kotlinParameters.isNewerThanOrEqualTo(KOTLINC_1_5_0) ? "b:I" : "a:I",
        age.fieldSignature().asString());
    assertEquals("getAge()I", age.getterSignature().asString());
    assertEquals("setAge(I)V", age.setterSignature().asString());
  }

  @Test
  public void smokeTest_setterApp() throws Exception {
    Path libJar = propertyTypeLibJarMap.getForConfiguration(kotlinc, targetVersion);

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(PKG_PREFIX + "/fragile_property_only_setter", "setter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".fragile_property_only_setter.Setter_userKt")
        .assertSuccessWithOutputLines();
  }

  @Test
  public void testMetadataInProperty_setterOnly() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addClasspathFiles(kotlinc.getKotlinStdlibJar(), kotlinc.getKotlinAnnotationJar())
            .addProgramFiles(propertyTypeLibJarMap.getForConfiguration(kotlinc, targetVersion))
            // Keep property setters (and users)
            .addKeepRules("-keep class **.Person { <init>(...); }")
            .addKeepRules("-keepclassmembers class **.Person { void set*(...); }")
            .addKeepRules("-keepclassmembers class **.Person { void aging(); }")
            .addKeepRules("-keepclassmembers class **.Person { void change*(...); }")
            // Keep LibKt extension methods
            .addKeepRules("-keep class **.LibKt { <methods>; }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectSetterOnly)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), kotlinc, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(PKG_PREFIX + "/fragile_property_only_setter", "setter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile(kotlinParameters.isOlderThan(KOTLINC_1_4_20));
    if (kotlinParameters.isOlderThan(KOTLINC_1_4_20)) {
      return;
    }

    testForJvm(parameters)
        .addRunClasspathFiles(kotlinc.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".fragile_property_only_setter.Setter_userKt")
        .assertSuccessWithOutputLines();
  }

  private void inspectSetterOnly(CodeInspector inspector) {
    String personClassName = PKG + ".fragile_property_lib.Person";
    ClassSubject person = inspector.clazz(personClassName);
    assertThat(person, isPresentAndNotRenamed());

    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = person.getKmClass();
    assertThat(kmClass, isPresent());

    KmPropertySubject name = kmClass.kmPropertyWithUniqueName("name");
    assertThat(name, isPresent());
    assertThat(name, not(isExtensionProperty()));
    // Oddly, the signature of field and getter are present even if there is only a setter:
    // #      KmProperty{
    // #        flags: 1798,
    // #        name: name,
    // #        receiverParameterType: null,
    // #        returnType: KmType{
    // #          flags: 0,
    // #          classifier: Class(name=kotlin/String),
    // #          arguments: KmTypeProjection[],
    // #          abbreviatedType: null,
    // #          outerType: null,
    // #          raw: false,
    // #          annotations: KmAnnotion[],
    // #        },
    // #        typeParameters: KmTypeParameter[],
    // #        getterFlags: 6,
    // #        setterFlags: 6,
    // #        setterParameter: null,
    // #        jvmFlags: 0,
    // #        fieldSignature: name:Ljava/lang/String;,
    // #        getterSignature: getName()Ljava/lang/String;,
    // #        setterSignature: setName(Ljava/lang/String;)V,
    assertEquals("name:Ljava/lang/String;", name.fieldSignature().asString());
    assertEquals("getName()Ljava/lang/String;", name.getterSignature().asString());
    assertEquals("setName(Ljava/lang/String;)V", name.setterSignature().asString());

    KmPropertySubject familyName = kmClass.kmPropertyWithUniqueName("familyName");
    assertThat(familyName, not(isPresent()));

    FieldSubject ageField = person.uniqueFieldWithOriginalName("age");
    assertThat(ageField, isAbsent());

    KmPropertySubject age = kmClass.kmPropertyWithUniqueName("age");
    assertThat(age, isPresent());
    assertThat(age, not(isExtensionProperty()));
    assertEquals("age:I", age.fieldSignature().asString());
    assertEquals("getAge()I", age.getterSignature().asString());
    assertEquals("setAge(I)V", age.setterSignature().asString());
  }
}
