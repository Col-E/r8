// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.kotlin.KotlinClass.Visibility;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
import com.android.tools.r8.utils.DexInspector.FieldSubject;
import java.nio.file.Path;
import org.junit.Test;

public class R8KotlinPropertiesTest extends AbstractR8KotlinTestBase {

  private static final String PACKAGE_NAME = "properties";

  private static final String JAVA_LANG_STRING = "java.lang.String";

  // This is the name of the Jasmin-generated class which contains the "main" method which will
  // invoke the tested method.
  private static final String JASMIN_MAIN_CLASS = "properties.TestMain";

  private static final KotlinClass MUTABLE_PROPERTY_CLASS =
      new KotlinClass("properties.MutableProperty")
          .addProperty("privateProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicProp", JAVA_LANG_STRING, Visibility.PUBLIC)
          .addProperty("primitiveProp", "int", Visibility.PUBLIC);

  private static final KotlinClass USER_DEFINED_PROPERTY_CLASS =
      new KotlinClass("properties.UserDefinedProperty")
          .addProperty("durationInMilliSeconds", "int", Visibility.PUBLIC)
          .addProperty("durationInSeconds", "int", Visibility.PUBLIC);

  private static final KotlinClass LATE_INIT_PROPERTY_CLASS =
      new KotlinClass("properties.LateInitProperty")
          .addProperty("privateLateInitProp", JAVA_LANG_STRING, Visibility.PRIVATE)
          .addProperty("protectedLateInitProp", JAVA_LANG_STRING, Visibility.PROTECTED)
          .addProperty("internalLateInitProp", JAVA_LANG_STRING, Visibility.INTERNAL)
          .addProperty("publicLateInitProp", JAVA_LANG_STRING, Visibility.PUBLIC);

  @Test
  public void testMutableProperty_getterAndSetterAreRemoveIfNotUsed() throws Exception {
    addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_noUseOfProperties");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      for (String propertyName : MUTABLE_PROPERTY_CLASS.properties.keySet()) {
        checkMethodIsAbsent(classSubject,
            MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName));
        checkMethodIsAbsent(classSubject,
            MUTABLE_PROPERTY_CLASS.getSetterForProperty(propertyName));
      }
    });
  }

  @Test
  public void testMutableProperty_privateIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_usePrivateProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "privateProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING, propertyName);
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
    addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_useProtectedProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "protectedProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING, propertyName);

      // Protected property has private field.
      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getter);
      }
    });
  }

  @Test
  public void testMutableProperty_internalIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_useInternalProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "internalProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING, propertyName);

      // Internal property has private field
      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getter);
      }
    });
  }

  @Test
  public void testMutableProperty_publicIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_usePublicProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "publicProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, JAVA_LANG_STRING, propertyName);

      // Public property has private field
      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getter);
      }
    });
  }

  @Test
  public void testMutableProperty_primitivePropertyIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/MutablePropertyKt", "mutableProperty_usePrimitiveProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          MUTABLE_PROPERTY_CLASS.getClassName());
      String propertyName = "primitiveProp";
      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, "int", propertyName);

      MethodSignature getter = MUTABLE_PROPERTY_CLASS.getGetterForProperty(propertyName);
      MethodSignature setter = MUTABLE_PROPERTY_CLASS.getSetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getter);
        checkMethodIsAbsent(classSubject, setter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getter);
        checkMethodIsPresent(classSubject, setter);
      }
    });
  }

  @Test
  public void testLateInitProperty_getterAndSetterAreRemoveIfNotUsed() throws Exception {
    addMainToClasspath("properties/LateInitPropertyKt", "lateInitProperty_noUseOfProperties");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      for (String propertyName : LATE_INIT_PROPERTY_CLASS.properties.keySet()) {
        checkMethodIsAbsent(classSubject,
            LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
        checkMethodIsAbsent(classSubject,
            LATE_INIT_PROPERTY_CLASS.getSetterForProperty(propertyName));
      }
    });
  }

  @Test
  public void testLateInitProperty_privateIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/LateInitPropertyKt", "lateInitProperty_usePrivateLateInitProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
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
    addMainToClasspath("properties/LateInitPropertyKt",
        "lateInitProperty_useProtectedLateInitProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "protectedLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      if (!allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isProtected());
      }

      // Protected late init property have protected getter
      checkMethodIsAbsent(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
    });
  }

  @Test
  public void testLateInitProperty_internalIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/LateInitPropertyKt", "lateInitProperty_useInternalLateInitProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "internalLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      // Internal late init property have protected getter
      checkMethodIsAbsent(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
    });
  }

  @Test
  public void testLateInitProperty_publicIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/LateInitPropertyKt", "lateInitProperty_usePublicLateInitProp");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          LATE_INIT_PROPERTY_CLASS.getClassName());
      String propertyName = "publicLateInitProp";
      FieldSubject fieldSubject = classSubject.field(JAVA_LANG_STRING, propertyName);
      assertTrue("Field is absent", fieldSubject.isPresent());
      assertTrue(fieldSubject.getField().accessFlags.isPublic());

      // Internal late init property have protected getter
      checkMethodIsAbsent(classSubject,
          LATE_INIT_PROPERTY_CLASS.getGetterForProperty(propertyName));
    });
  }

  @Test
  public void testUserDefinedProperty_getterAndSetterAreRemoveIfNotUsed() throws Exception {
    addMainToClasspath("properties/UserDefinedPropertyKt", "userDefinedProperty_noUseOfProperties");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          USER_DEFINED_PROPERTY_CLASS.getClassName());
      for (String propertyName : USER_DEFINED_PROPERTY_CLASS.properties.keySet()) {
        checkMethodIsAbsent(classSubject,
            USER_DEFINED_PROPERTY_CLASS.getGetterForProperty(propertyName));
        checkMethodIsAbsent(classSubject,
            USER_DEFINED_PROPERTY_CLASS.getSetterForProperty(propertyName));
      }
    });
  }

  @Test
  public void testUserDefinedProperty_publicIsAlwaysInlined() throws Exception {
    addMainToClasspath("properties/UserDefinedPropertyKt", "userDefinedProperty_useProperties");
    runTest(PACKAGE_NAME, JASMIN_MAIN_CLASS, (app) -> {
      DexInspector dexInspector = new DexInspector(app);
      ClassSubject classSubject = checkClassExists(dexInspector,
          USER_DEFINED_PROPERTY_CLASS.getClassName());
      String propertyName = "durationInSeconds";
      // The 'wrapper' property is not assigned to a backing field, it only relies on the wrapped
      // property.
      checkFieldIsAbsent(classSubject, "int", "durationInSeconds");

      FieldSubject fieldSubject = checkFieldIsPresent(classSubject, "int",
          "durationInMilliSeconds");
      MethodSignature getter = USER_DEFINED_PROPERTY_CLASS.getGetterForProperty(propertyName);
      if (allowAccessModification) {
        assertTrue(fieldSubject.getField().accessFlags.isPublic());
        checkMethodIsAbsent(classSubject, getter);
      } else {
        assertTrue(fieldSubject.getField().accessFlags.isPrivate());
        checkMethodIsPresent(classSubject, getter);
      }
    });
  }

  /**
   * Generates a "main" class which invokes the given static method on the given klass. This new
   * class is then added to the test classpath.
   */
  private void addMainToClasspath(String klass, String method) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    ClassBuilder mainClassBuilder =
        builder.addClass(DescriptorUtils.getBinaryNameFromJavaType(JASMIN_MAIN_CLASS));
    mainClassBuilder.addMainMethod(
        "invokestatic " + klass + "/" + method + "()V",
        "return"
    );

    Path output = writeToZip(builder);
    addExtraClasspath(output);
  }
}
