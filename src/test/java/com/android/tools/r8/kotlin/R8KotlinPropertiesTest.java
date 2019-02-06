// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.kotlin.TestKotlinClass.KotlinProperty;
import com.android.tools.r8.kotlin.TestKotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import java.util.Map.Entry;
import java.util.function.Consumer;
import org.junit.Test;

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

  private Consumer<InternalOptions> disableAggressiveClassOptimizations =
      o -> {
        o.enableClassInlining = false;
        o.enableVerticalClassMerging = false;
        o.enableClassStaticizer = false;
      };

  @Test
  public void testMutableProperty_getterAndSetterAreRemoveIfNotUsed() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_noUseOfProperties");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      for (Entry<String, KotlinProperty> property : MUTABLE_PROPERTY_CLASS.properties.entrySet()) {
        MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(property.getKey());
        MethodSignature setter = MUTABLE_PROPERTY_CLASS.getSetterForProperty(property.getKey());
        if (property.getValue().getVisibility() == Visibility.PRIVATE) {
          // Private properties have no getter/setter
          checkMethodIsAbsent(classSubject, getter);
          checkMethodIsAbsent(classSubject, setter);
        } else {
          checkMethodIsRemoved(classSubject, getter);
          checkMethodIsRemoved(classSubject, setter);
        }
      }
    });
  }

  @Test
  public void testMutableProperty_privateIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_usePrivateProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);
      if (!allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      }

      // Private property has no getter or setter.
      checkMethodIsAbsent(classSubject, MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName));
      checkMethodIsAbsent(classSubject, MUTABLE_PROPERTY_CLASS.getSetterForProperty(propertyName));
    });
  }

  @Test
  public void testMutableProperty_protectedIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_useProtectedProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "protectedProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);

      // Protected property has private field.
      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getter);
      }
    });
  }

  @Test
  public void testMutableProperty_internalIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_useInternalProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);

      // Internal property has private field
      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getter);
      }
    });
  }

  @Test
  public void testMutableProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_usePublicProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, JAVA_LANG_STRING, propertyName);

      // Public property has private field
      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getter);
      }
    });
  }

  @Test
  public void testMutableProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/MutablePropertyKt",
        "mutableProperty_usePrimitiveProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsKept(classSubject, "int", propertyName);

      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      MethodSignature setter = MUTABLE_PROPERTY_CLASS.getSetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getter);
        checkMethodIsRemoved(classSubject, setter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getter);
        checkMethodIsKept(classSubject, setter);
      }
    });
  }

  @Test
  public void testLateInitProperty_getterAndSetterAreRemoveIfNotUsed() throws Exception {
    String mainClass = addMainToClasspath("properties/LateInitPropertyKt",
        "lateInitProperty_noUseOfProperties");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      for (Entry<String, KotlinProperty> property : LATE_INIT_PROPERTY_CLASS.properties
          .entrySet()) {
        MethodSignature getter = LATE_INIT_PROPERTY_CLASS.getGetterForProperty(property.getKey());
        MethodSignature setter = LATE_INIT_PROPERTY_CLASS.getSetterForProperty(property.getKey());
        if (property.getValue().getVisibility() == Visibility.PRIVATE) {
          // Private properties have no getter or setter.
          checkMethodIsAbsent(classSubject, getter);
          checkMethodIsAbsent(classSubject, setter);

        } else {
          checkMethodIsRemoved(classSubject, getter);
          checkMethodIsRemoved(classSubject, setter);
        }
      }
    });
  }

  @Test
  public void testLateInitProperty_privateIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/LateInitPropertyKt", "lateInitProperty_usePrivateLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      if (!allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }

      // Private late init property have no getter or setter.
      checkMethodIsAbsent(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
      checkMethodIsAbsent(classSubject,
          LATE_INIT_PROPERTY_CLASS.getSetterForProperty(propertyName));
    });
  }

  @Test
  public void testLateInitProperty_protectedIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath("properties/LateInitPropertyKt",
        "lateInitProperty_useProtectedLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "protectedLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      if (!allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isProtected());
      }

      // Protected late init property have protected getter
      checkMethodIsRemoved(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
    });
  }

  @Test
  public void testLateInitProperty_internalIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/LateInitPropertyKt", "lateInitProperty_useInternalLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      // Internal late init property have protected getter
      checkMethodIsRemoved(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
    });
  }

  @Test
  public void testLateInitProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/LateInitPropertyKt", "lateInitProperty_usePublicLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      // Internal late init property have protected getter
      checkMethodIsRemoved(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
    });
  }

  @Test
  public void testUserDefinedProperty_getterAndSetterAreRemoveIfNotUsed() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/UserDefinedPropertyKt", "userDefinedProperty_noUseOfProperties");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          USER_DEFINED_PROPERTY_CLASS.getClassName());
      for (String propertyName : USER_DEFINED_PROPERTY_CLASS.properties.keySet()) {
        checkMethodIsRemoved(classSubject,
            USER_DEFINED_PROPERTY_CLASS.getGetterForProperty(propertyName));
        checkMethodIsRemoved(classSubject,
            USER_DEFINED_PROPERTY_CLASS.getSetterForProperty(propertyName));
      }
    });
  }

  @Test
  public void testUserDefinedProperty_publicIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties/UserDefinedPropertyKt", "userDefinedProperty_useProperties");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject classSubject = checkClassIsKept(codeInspector,
          USER_DEFINED_PROPERTY_CLASS.getClassName());
      String propertyName = "durationInSeconds";
      // The 'wrapper' property is not assigned to a backing field, it only relies on the wrapped
      // property.
      checkFieldIsAbsent(classSubject, "int", "durationInSeconds");

      FieldSubject fieldSubject = checkFieldIsKept(classSubject, "int",
          "durationInMilliSeconds");
      MethodSignature getter = USER_DEFINED_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(classSubject, getter);
      }
    });
  }

  @Test
  public void testCompanionProperty_primitivePropertyCannotBeInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePrimitiveProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector,
          "properties.CompanionProperties");
      ClassSubject companionClass = checkClassIsKept(codeInspector,
          COMPANION_PROPERTY_CLASS.getClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, "int", propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = COMPANION_PROPERTY_CLASS
          .getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = COMPANION_PROPERTY_CLASS
          .getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined because we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(companionClass, getter);
      checkMethodIsKept(companionClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testCompanionProperty_privatePropertyIsAlwaysInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePrivateProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector,
          "properties.CompanionProperties");
      ClassSubject companionClass = checkClassIsKept(codeInspector,
          COMPANION_PROPERTY_CLASS.getClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = COMPANION_PROPERTY_CLASS
          .getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = COMPANION_PROPERTY_CLASS
          .getSetterForProperty(propertyName);

      // Because the getter/setter are private, they can only be called from another method in the
      // class. If this is an instance method, they will be called on 'this' which is known to be
      // non-null, thus the getter/setter can be inlined if their code is small enough.
      // Because the backing field is private, they will call into an accessor (static) method. If
      // access relaxation is enabled, this accessor can be removed.
      checkMethodIsAbsent(companionClass, getter);
      checkMethodIsAbsent(companionClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testCompanionProperty_internalPropertyCannotBeInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_useInternalProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector,
          "properties.CompanionProperties");
      ClassSubject companionClass = checkClassIsKept(codeInspector,
          COMPANION_PROPERTY_CLASS.getClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = COMPANION_PROPERTY_CLASS
          .getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = COMPANION_PROPERTY_CLASS
          .getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined because we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(companionClass, getter);
      checkMethodIsKept(companionClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testCompanionProperty_publicPropertyCannotBeInlined() throws Exception {
    String mainClass = addMainToClasspath(
        "properties.CompanionPropertiesKt", "companionProperties_usePublicProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector,
          "properties.CompanionProperties");
      ClassSubject companionClass = checkClassIsKept(codeInspector,
          COMPANION_PROPERTY_CLASS.getClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = COMPANION_PROPERTY_CLASS
          .getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = COMPANION_PROPERTY_CLASS
          .getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined because we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(companionClass, getter);
      checkMethodIsKept(companionClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testCompanionProperty_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePrivateLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector, testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Because the getter/setter are private, they can only be called from another method in the
      // class. If this is an instance method, they will be called on 'this' which is known to be
      // non-null, thus the getter/setter can be inlined if their code is small enough.
      // Because the backing field is private, they will call into an accessor (static) method. If
      // access relaxation is enabled, this accessor can be removed.
      checkMethodIsAbsent(companionClass, getter);
      checkMethodIsAbsent(companionClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testCompanionProperty_internalLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_useInternalLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector, testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined because we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(companionClass, getter);
      checkMethodIsKept(companionClass, setter);
      assertTrue(fieldSubject.getField().accessFlags.isPublic());
    });
  }

  @Test
  public void testCompanionProperty_publicLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinCompanionClass testedClass = COMPANION_LATE_INIT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath("properties.CompanionLateInitPropertiesKt",
        "companionLateInitProperties_usePublicLateInitProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject outerClass = checkClassIsKept(codeInspector, testedClass.getOuterClassName());
      ClassSubject companionClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(outerClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined because we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(companionClass, getter);
      checkMethodIsKept(companionClass, setter);
      assertTrue(fieldSubject.getField().accessFlags.isPublic());
    });
  }

  @Test
  public void testObjectClass_primitivePropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_usePrimitiveProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, "int", propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined when we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(objectClass, getter);
      checkMethodIsKept(objectClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testObjectClass_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_usePrivateProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // A private property has no getter/setter.
      checkMethodIsAbsent(objectClass, getter);
      checkMethodIsAbsent(objectClass, setter);

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testObjectClass_internalPropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useInternalProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined when we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(objectClass, getter);
      checkMethodIsKept(objectClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testObjectClass_publicPropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_usePublicProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Getter and setter cannot be inlined when we don't know if null check semantic is
      // preserved.
      checkMethodIsKept(objectClass, getter);
      checkMethodIsKept(objectClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testObjectClass_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useLateInitPrivateProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // A private property has no getter/setter.
      checkMethodIsAbsent(objectClass, getter);
      checkMethodIsAbsent(objectClass, setter);

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testObjectClass_internalLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useLateInitInternalProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      checkMethodIsRemoved(objectClass, getter);
      checkMethodIsRemoved(objectClass, setter);
      assertTrue(fieldSubject.getField().accessFlags.isPublic());
    });
  }

  @Test
  public void testObjectClass_publicLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = OBJECT_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.ObjectPropertiesKt", "objectProperties_useLateInitPublicProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      checkMethodIsRemoved(objectClass, getter);
      checkMethodIsRemoved(objectClass, setter);
      assertTrue(fieldSubject.getField().accessFlags.isPublic());
    });
  }

  @Test
  public void testFileLevel_primitivePropertyIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_usePrimitiveProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, "int", propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(objectClass, getter);
        checkMethodIsRemoved(objectClass, setter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(objectClass, getter);
        checkMethodIsKept(objectClass, setter);
      }
    });
  }

  @Test
  public void testFileLevel_privatePropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_usePrivateProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // A private property has no getter/setter.
      checkMethodIsAbsent(objectClass, getter);
      checkMethodIsAbsent(objectClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testFileLevel_internalPropertyGetterIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useInternalProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      // We expect getter to be inlined when access (of the backing field) is relaxed to public.
      // Note: the setter is considered as a regular method (because of KotlinC adding extra null
      // checks), thus we cannot say if the setter would be inlined or not by R8.
      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(objectClass, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(objectClass, getter);
      }
    });
  }

  @Test
  public void testFileLevel_publicPropertyGetterIsInlinedIfAccessIsRelaxed() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_usePublicProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      // We expect getter to be inlined when access (of the backing field) is relaxed to public.
      // On the other hand, the setter is considered as a regular method (because of null checks),
      // thus we cannot say if it can be inlined or not.
      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);

      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsRemoved(objectClass, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsKept(objectClass, getter);
      }
    });
  }

  @Test
  public void testFileLevel_privateLateInitPropertyIsAlwaysInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useLateInitPrivateProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject fileClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "privateLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(fileClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // A private property has no getter/setter.
      checkMethodIsAbsent(fileClass, getter);
      checkMethodIsAbsent(fileClass, setter);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
      }
    });
  }

  @Test
  public void testFileLevel_internalLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useLateInitInternalProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      // Field is public and getter/setter is only called from one place so we expect to always
      // inline it.
      checkMethodIsRemoved(objectClass, getter);
      checkMethodIsRemoved(objectClass, setter);
    });
  }

  @Test
  public void testFileLevel_publicLateInitPropertyCannotBeInlined() throws Exception {
    final TestKotlinClass testedClass = FILE_PROPERTY_CLASS;
    String mainClass = addMainToClasspath(
        "properties.FilePropertiesKt", "fileProperties_useLateInitPublicProp");
    runTest(PACKAGE_NAME, mainClass, disableAggressiveClassOptimizations, (app) -> {
      CodeInspector codeInspector = new CodeInspector(app);
      ClassSubject objectClass = checkClassIsKept(codeInspector, testedClass.getClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = checkFieldIsKept(objectClass, JAVA_LANG_STRING, propertyName);
      assertTrue(fieldSubject.getField().accessFlags.isStatic());

      MemberNaming.MethodSignature getter = testedClass.getGetterForProperty(propertyName);
      MemberNaming.MethodSignature setter = testedClass.getSetterForProperty(propertyName);

      checkMethodIsRemoved(objectClass, getter);
      checkMethodIsRemoved(objectClass, setter);
      assertTrue(fieldSubject.getField().accessFlags.isPublic());
    });
  }

}
