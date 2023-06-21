// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.KotlinTestParameters;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.Collection;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class R8KotlinPropertiesTest extends AbstractR8KotlinTestBase {

  private static final String PACKAGE_NAME = "properties";

  private static final String JAVA_LANG_STRING = "java.lang.String";

  private static final TestKotlinClass MUTABLE_PROPERTY_CLASS =
      new TestKotlinClass("properties.MutableProperty")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC);

  private static final TestKotlinClass USER_DEFINED_PROPERTY_CLASS =
      new TestKotlinClass("properties.UserDefinedProperty")
          .addProperty("durationInMilliSeconds", "int", Visibility.PUBLIC)
          .addProperty("durationInSeconds", "int", Visibility.PUBLIC);

  private static final TestKotlinClass LATE_INIT_PROPERTY_CLASS =
      new TestKotlinClass("properties.LateInitProperty")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedLateInitProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinCompanionClass COMPANION_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinCompanionClass COMPANION_LATE_INIT_PROPERTY_CLASS =
      new TestKotlinCompanionClass("properties.CompanionLateInitProperties")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestKotlinClass OBJECT_PROPERTY_CLASS =
      new TestKotlinClass("properties.ObjectProperties")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private static final TestFileLevelKotlinClass FILE_PROPERTY_CLASS =
      new TestFileLevelKotlinClass("properties.FilePropertiesKt")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC)
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  private final Consumer<InternalOptions> disableAggressiveClassOptimizations =
      o -> {
        o.enableClassInlining = false;
        o.enableVerticalClassMerging = false;
      };

  @Parameterized.Parameters(name = "{0}, {1}, allowAccessModification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        getKotlinTestParameters().withAllCompilersAndTargetVersions().build(),
        BooleanUtils.values());
  }

  public R8KotlinPropertiesTest(
      TestParameters parameters,
      KotlinTestParameters kotlinParameters,
      boolean allowAccessModification) {
    super(parameters, kotlinParameters, allowAccessModification);
  }

  @Test
  public void testMutableProperty_classIsRemovedIfNotUsed() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_noUseOfProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testMutableProperty_privateIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
              String propertyName = "privateProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isPrivate());

              // Private property has no getter or setter.
              checkMethodIsAbsent(
                  classSubject, MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName));
              checkMethodIsAbsent(
                  classSubject, MUTABLE_PROPERTY_CLASS.getSetterForProperty(propertyName));
            });
  }

  @Test
  public void testMutableProperty_protectedIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_useProtectedProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
              String propertyName = "protectedProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);

              // Protected property has private field.
              MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(classSubject, getter);
            });
  }

  @Test
  public void testMutableProperty_internalIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
              String propertyName = "internalProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);

              // Internal property has private field
              MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(classSubject, getter);
            });
  }

  @Test
  public void testMutableProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
              String propertyName = "publicProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);

              // Public property has private field
              MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(classSubject, getter);
            });
  }

  @Test
  public void testMutableProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, MUTABLE_PROPERTY_CLASS.getClassName());
              String propertyName = "primitiveProp";
              FieldSubject fieldSubject = checkFieldIsKept(classSubject, "int", propertyName);

              MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
              MethodSignature setter = MUTABLE_PROPERTY_CLASS.getSetterForProperty(propertyName);
              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(classSubject, getter);
              checkMethodIsRemoved(classSubject, setter);
            });
  }

  @Test
  public void testLateInitProperty_classIsRemovedIfNotUsed() throws Exception {
    String mainClass =
        addMainToClasspath("properties/LateInitPropertyKt", "lateInitProperty_noUseOfProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_privateIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/LateInitPropertyKt", "lateInitProperty_usePrivateLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, LATE_INIT_PROPERTY_CLASS.getClassName());
              String propertyName = "privateLateInitProp";
              FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
              assertTrue("Field is absent", fieldSubject.isPresent());
              assertTrue(fieldSubject.getField().isPrivate());

              // Private late init property have no getter or setter.
              checkMethodIsAbsent(
                  classSubject, LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
              checkMethodIsAbsent(
                  classSubject, LATE_INIT_PROPERTY_CLASS.getSetterForProperty(propertyName));
            });
  }

  @Test
  public void testLateInitProperty_protectedIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/LateInitPropertyKt",
        "lateInitProperty_useProtectedLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_internalIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/LateInitPropertyKt", "lateInitProperty_useInternalLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testLateInitProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/LateInitPropertyKt", "lateInitProperty_usePublicLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> checkClassIsRemoved(inspector, LATE_INIT_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testUserDefinedProperty_classIsRemovedIfNotUsed() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/UserDefinedPropertyKt", "userDefinedProperty_noUseOfProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector ->
                checkClassIsRemoved(inspector, USER_DEFINED_PROPERTY_CLASS.getClassName()));
  }

  @Test
  public void testUserDefinedProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/UserDefinedPropertyKt", "userDefinedProperty_useProperties");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, USER_DEFINED_PROPERTY_CLASS.getClassName());
                return;
              }

              ClassSubject classSubject =
                  checkClassIsKept(inspector, USER_DEFINED_PROPERTY_CLASS.getClassName());
              String propertyName = "durationInSeconds";
              // The 'wrapper' property is not assigned to a backing field, it only relies on the
              // wrapped property.
              checkFieldIsAbsent(classSubject, "int", "durationInSeconds");

              FieldSubject fieldSubject =
                  checkFieldIsKept(classSubject, "int", "durationInMilliSeconds");
              MethodSignature getter =
                  USER_DEFINED_PROPERTY_CLASS.getGetterForProperty(propertyName);
              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(classSubject, getter);
            });
  }

  @Test
  public void testCompanionProperty_primitivePropertyCannotBeInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());

              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, "properties.CompanionProperties");
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, "properties.CompanionProperties");
              String propertyName = "primitiveProp";
              FieldSubject fieldSubject = checkFieldIsKept(outerClass, "int", propertyName);
              assertTrue(fieldSubject.getField().isStatic());
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testCompanionProperty_privatePropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());

              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, "properties.CompanionProperties");
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, "properties.CompanionProperties");
              String propertyName = "privateProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              // Because the getter/setter are private, they can only be called from another method
              // in the class. If this is an instance method, they will be called on 'this' which is
              // known to be non-null, thus the getter/setter can be inlined if their code is small
              // enough. Because the backing field is private, they will call into an accessor
              // (static) method.
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testCompanionProperty_internalPropertyCannotBeInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, "properties.CompanionProperties");
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, "properties.CompanionProperties");
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());
              String propertyName = "internalProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testCompanionProperty_publicPropertyCannotBeInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, COMPANION_PROPERTY_CLASS.getClassName());

              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, "properties.CompanionProperties");
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, "properties.CompanionProperties");
              String propertyName = "publicProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testCompanionProperty_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePrivateLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, testedClass.getClassName());

              if (testParameters.isAccessModificationEnabled(allowAccessModification)
                  || (testParameters.isDexRuntime()
                      && testParameters.getApiLevel().isGreaterThan(AndroidApiLevel.B))) {
                checkClassIsRemoved(inspector, testedClass.getOuterClassName());
                return;
              }

              ClassSubject outerClass =
                  checkClassIsKept(inspector, testedClass.getOuterClassName());
              String propertyName = "privateLateInitProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              // Because the getter/setter are private, they can only be called from another method
              // in the class. If this is an instance method, they will be called on 'this' which is
              // known to be non-null, thus the getter/setter can be inlined if their code is small
              // enough. Because the backing field is private, they will call into an accessor
              // (static) method. If access relaxation is enabled, this accessor can be removed.
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testCompanionProperty_internalLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_useInternalLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              checkClassIsRemoved(inspector, testedClass.getClassName());
              checkClassIsRemoved(inspector, testedClass.getOuterClassName());
            });
  }

  @Test
  public void testCompanionProperty_publicLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePublicLateInitProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_primitivePropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "primitiveProp";
              FieldSubject fieldSubject = checkFieldIsKept(objectClass, "int", propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);
              checkMethodIsKept(objectClass, getter);
              checkMethodIsRemoved(objectClass, setter);
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testObjectClass_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "privateProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              // A private property has no getter/setter.
              checkMethodIsAbsent(objectClass, getter);
              checkMethodIsAbsent(objectClass, setter);

              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testObjectClass_internalPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "internalProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              checkMethodIsKept(objectClass, getter);
              checkMethodIsRemoved(objectClass, setter);

              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testObjectClass_publicPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "publicProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              checkMethodIsKept(objectClass, getter);
              checkMethodIsRemoved(objectClass, setter);

              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testObjectClass_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useLateInitPrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "privateLateInitProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              // A private property has no getter/setter.
              checkMethodIsAbsent(objectClass, getter);
              checkMethodIsAbsent(objectClass, setter);

              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testObjectClass_internalLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useLateInitInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testObjectClass_publicLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useLateInitPublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_primitivePropertyIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_usePrimitiveProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "primitiveProp";
              FieldSubject fieldSubject = checkFieldIsKept(objectClass, "int", propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(objectClass, getter);
              checkMethodIsRemoved(objectClass, setter);
            });
  }

  @Test
  public void testFileLevel_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_usePrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "privateProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              // A private property has no getter/setter.
              checkMethodIsAbsent(objectClass, getter);
              checkMethodIsAbsent(objectClass, setter);
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testFileLevel_internalPropertyGetterIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "internalProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              // We expect getter to be inlined when access (of the backing field) is relaxed to
              // public.
              //
              // Note: the setter is considered as a regular method (because of KotlinC adding extra
              // null checks), thus we cannot say if the setter would be inlined or not by R8.
              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(objectClass, getter);
            });
  }

  @Test
  public void testFileLevel_publicPropertyGetterIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_usePublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject objectClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "publicProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              // We expect getter to be inlined when access (of the backing field) is relaxed to
              // public. On the other hand, the setter is considered as a regular method (because of
              // null checks), thus we cannot say if it can be inlined or not.
              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);

              assertTrue(fieldSubject.getField().isPrivate());
              checkMethodIsKept(objectClass, getter);
            });
  }

  @Test
  public void testFileLevel_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useLateInitPrivateProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(
            inspector -> {
              if (testParameters.isAccessModificationEnabled(allowAccessModification)) {
                checkClassIsRemoved(inspector, testedClass.getClassName());
                return;
              }

              ClassSubject fileClass = checkClassIsKept(inspector, testedClass.getClassName());
              String propertyName = "privateLateInitProp";
              FieldSubject fieldSubject =
                  checkFieldIsKept(fileClass, JAVA_LANG_STRING, propertyName);
              assertTrue(fieldSubject.getField().isStatic());

              MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
              MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

              // A private property has no getter/setter.
              checkMethodIsAbsent(fileClass, getter);
              checkMethodIsAbsent(fileClass, setter);
              assertTrue(fieldSubject.getField().isPrivate());
            });
  }

  @Test
  public void testFileLevel_internalLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useLateInitInternalProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

  @Test
  public void testFileLevel_publicLateInitPropertyIsInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useLateInitPublicProp");
    runTest(
            PACKAGE_NAME,
            mainClass,
            testBuilder -> testBuilder.addOptionsModification(disableAggressiveClassOptimizations))
        .inspect(inspector -> checkClassIsRemoved(inspector, testedClass.getClassName()));
  }

}
