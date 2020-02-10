// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin.metadata;

import static com.android.tools.r8.KotlinCompilerTool.KOTLINC;
import static com.android.tools.r8.utils.codeinspector.Matchers.isExtensionProperty;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.KotlinTargetVersion;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.KmClassSubject;
import com.android.tools.r8.utils.codeinspector.KmPropertySubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MetadataRewriteInPropertyTest extends KotlinMetadataTestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0} target: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withCfRuntimes().build(), KotlinTargetVersion.values());
  }

  public MetadataRewriteInPropertyTest(
      TestParameters parameters, KotlinTargetVersion targetVersion) {
    super(targetVersion);
    this.parameters = parameters;
  }

  private static final Map<KotlinTargetVersion, Path> propertyTypeLibJarMap = new HashMap<>();

  @BeforeClass
  public static void createLibJar() throws Exception {
    String propertyTypeLibFolder = PKG_PREFIX + "/fragile_property_lib";
    for (KotlinTargetVersion targetVersion : KotlinTargetVersion.values()) {
      Path propertyTypeLibJar =
          kotlinc(KOTLINC, targetVersion)
              .addSourceFiles(getKotlinFileInTest(propertyTypeLibFolder, "lib"))
              .compile();
      propertyTypeLibJarMap.put(targetVersion, propertyTypeLibJar);
    }
  }

  @Test
  public void testMetadataInProperty_getterOnly() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(propertyTypeLibJarMap.get(targetVersion))
            // Keep property getters
            .addKeepRules("-keep class **.Person { <init>(...); }")
            .addKeepRules("-keepclassmembers class **.Person { *** get*(); }")
            .addKeepAttributes(ProguardKeepAttributes.RUNTIME_VISIBLE_ANNOTATIONS)
            .compile()
            .inspect(this::inspectGetterOnly)
            .writeToZip();

    Path output =
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(PKG_PREFIX + "/fragile_property_only_getter", "getter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".fragile_property_only_getter.Getter_userKt")
        .assertSuccessWithOutputLines("true", "false", "Hey Jude");
  }

  private void inspectGetterOnly(CodeInspector inspector) {
    String personClassName = PKG + ".fragile_property_lib.Person";
    ClassSubject person = inspector.clazz(personClassName);
    assertThat(person, isPresent());
    assertThat(person, not(isRenamed()));

    FieldSubject backingField = person.uniqueFieldWithName("name");
    assertThat(backingField, isRenamed());
    MethodSubject getterForName = person.uniqueMethodWithName("getName");
    assertThat(getterForName, isPresent());
    assertThat(getterForName, not(isRenamed()));
    MethodSubject setterForName = person.uniqueMethodWithName("setName");
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
    assertNull(name.setterSignature());

    KmPropertySubject familyName = kmClass.kmPropertyWithUniqueName("familyName");
    assertThat(familyName, isPresent());
    assertThat(familyName, not(isExtensionProperty()));
    // No backing field for property `familyName`
    assertNull(familyName.fieldSignature());
    assertNotNull(familyName.getterSignature());
    // No setter for property `familyName`
    assertNull(familyName.setterSignature());

    KmPropertySubject age = kmClass.kmPropertyWithUniqueName("age");
    assertThat(age, isPresent());
    assertThat(age, not(isExtensionProperty()));
    assertNotNull(age.fieldSignature());
    assertNotNull(age.getterSignature());
    assertNull(name.setterSignature());
  }

  @Test
  public void testMetadataInProperty_setterOnly() throws Exception {
    Path libJar =
        testForR8(parameters.getBackend())
            .addProgramFiles(propertyTypeLibJarMap.get(targetVersion))
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
        kotlinc(parameters.getRuntime().asCf(), KOTLINC, targetVersion)
            .addClasspathFiles(libJar)
            .addSourceFiles(
                getKotlinFileInTest(PKG_PREFIX + "/fragile_property_only_setter", "setter_user"))
            .setOutputPath(temp.newFolder().toPath())
            .compile();

    testForJvm()
        .addRunClasspathFiles(ToolHelper.getKotlinStdlibJar(), libJar)
        .addClasspath(output)
        .run(parameters.getRuntime(), PKG + ".fragile_property_only_setter.Setter_userKt")
        .assertSuccessWithOutputLines();
  }

  private void inspectSetterOnly(CodeInspector inspector) {
    String personClassName = PKG + ".fragile_property_lib.Person";
    ClassSubject person = inspector.clazz(personClassName);
    assertThat(person, isPresent());
    assertThat(person, not(isRenamed()));

    // API entry is kept, hence the presence of Metadata.
    KmClassSubject kmClass = person.getKmClass();
    assertThat(kmClass, isPresent());

    KmPropertySubject name = kmClass.kmPropertyWithUniqueName("name");
    assertThat(name, isPresent());
    assertThat(name, not(isExtensionProperty()));
    assertNull(name.fieldSignature());
    assertNull(name.getterSignature());
    assertNotNull(name.setterSignature());

    KmPropertySubject familyName = kmClass.kmPropertyWithUniqueName("familyName");
    assertThat(familyName, not(isPresent()));

    KmPropertySubject age = kmClass.kmPropertyWithUniqueName("age");
    assertThat(age, isPresent());
    assertThat(age, not(isExtensionProperty()));
    assertNotNull(age.fieldSignature());
    assertNull(age.getterSignature());
    assertNotNull(age.setterSignature());
  }
}
