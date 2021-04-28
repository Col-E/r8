// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingClassInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.diagnostic.MissingFieldInfo;
import com.android.tools.r8.diagnostic.MissingMethodInfo;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoundMissingDefinitionsDiagnosticSubject
    extends FoundDiagnosticSubject<MissingDefinitionsDiagnostic> {

  private final Map<ClassReference, MissingClassInfo> missingClasses = new HashMap<>();
  private final Map<FieldReference, MissingFieldInfo> missingFields = new HashMap<>();
  private final Map<MethodReference, MissingMethodInfo> missingMethods = new HashMap<>();

  public FoundMissingDefinitionsDiagnosticSubject(MissingDefinitionsDiagnostic diagnostic) {
    super(diagnostic);
    diagnostic.getMissingDefinitions().stream()
        .forEach(
            missingDefinitionInfo -> {
              if (missingDefinitionInfo.isMissingClass()) {
                MissingClassInfo missingClassInfo = missingDefinitionInfo.asMissingClass();
                missingClasses.put(missingClassInfo.getClassReference(), missingClassInfo);
              } else if (missingDefinitionInfo.isMissingField()) {
                MissingFieldInfo missingFieldInfo = missingDefinitionInfo.asMissingField();
                missingFields.put(missingFieldInfo.getFieldReference(), missingFieldInfo);
              } else {
                assert missingDefinitionInfo.isMissingMethod();
                MissingMethodInfo missingMethodInfo = missingDefinitionInfo.asMissingMethod();
                missingMethods.put(missingMethodInfo.getMethodReference(), missingMethodInfo);
              }
            });
  }

  public FoundMissingDefinitionsDiagnosticSubject assertHasMessage(String expectedMessage) {
    assertEquals(expectedMessage, getDiagnostic().getDiagnosticMessage());
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClass(Class<?> clazz) {
    return assertIsMissingClass(Reference.classFromClass(clazz));
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClass(
      ClassReference classReference) {
    assertTrue(missingClasses.containsKey(classReference));
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClassWithExactContexts(
      ClassReference classReference, DefinitionContext... expectedContexts) {
    return assertIsMissingClassWithExactContexts(classReference, Arrays.asList(expectedContexts));
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClassWithExactContexts(
      ClassReference classReference, List<DefinitionContext> expectedContexts) {
    return inspectMissingClassInfo(
        classReference,
        missingClassInfoSubject -> missingClassInfoSubject.assertExactContexts(expectedContexts));
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsAllMissingClasses(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      assertIsMissingClass(clazz);
    }
    return assertNumberOfMissingClasses(classes.length);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingField(
      FieldReference fieldReference) {
    assertTrue(missingFields.containsKey(fieldReference));
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsAllMissingFields(
      FieldReference... fieldReferences) {
    for (FieldReference fieldReference : fieldReferences) {
      assertIsMissingField(fieldReference);
    }
    return assertNumberOfMissingFields(fieldReferences.length);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingMethod(
      MethodReference methodReference) {
    assertTrue(missingMethods.containsKey(methodReference));
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsAllMissingMethods(
      MethodReference... methodReferences) {
    for (MethodReference methodReference : methodReferences) {
      assertIsMissingMethod(methodReference);
    }
    return assertNumberOfMissingMethods(methodReferences.length);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNoMissingClasses() {
    return assertNumberOfMissingClasses(0);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNoMissingFields() {
    return assertNumberOfMissingFields(0);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNoMissingMethods() {
    return assertNumberOfMissingMethods(0);
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNumberOfMissingClasses(int expected) {
    assertEquals(expected, missingClasses.size());
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNumberOfMissingFields(int expected) {
    assertEquals(expected, missingFields.size());
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNumberOfMissingMethods(int expected) {
    assertEquals(expected, missingMethods.size());
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject applyIf(
      boolean condition, ThrowableConsumer<FoundMissingDefinitionsDiagnosticSubject> thenConsumer) {
    return applyIf(condition, thenConsumer, ThrowableConsumer.empty());
  }

  public FoundMissingDefinitionsDiagnosticSubject applyIf(
      boolean condition,
      ThrowableConsumer<FoundMissingDefinitionsDiagnosticSubject> thenConsumer,
      ThrowableConsumer<FoundMissingDefinitionsDiagnosticSubject> elseConsumer) {
    if (condition) {
      thenConsumer.acceptWithRuntimeException(this);
    } else {
      elseConsumer.acceptWithRuntimeException(this);
    }
    return this;
  }

  public FoundMissingDefinitionsDiagnosticSubject inspectMissingClassInfo(
      ClassReference classReference,
      ThrowableConsumer<FoundMissingDefinitionInfoSubject> inspector) {
    MissingDefinitionInfo missingDefinitionInfo = missingClasses.get(classReference);
    assertNotNull(missingDefinitionInfo);
    inspector.acceptWithRuntimeException(
        new FoundMissingDefinitionInfoSubject(missingDefinitionInfo));
    return this;
  }
}
