// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ThrowableConsumer;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.MissingDefinitionsDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoundMissingDefinitionsDiagnosticSubject
    extends FoundDiagnosticSubject<MissingDefinitionsDiagnostic> {

  private final Map<ClassReference, MissingDefinitionInfo> missingClasses = new HashMap<>();

  public FoundMissingDefinitionsDiagnosticSubject(MissingDefinitionsDiagnostic diagnostic) {
    super(diagnostic);
    diagnostic
        .getMissingDefinitions()
        .forEach(
            missingDefinitionInfo ->
                missingDefinitionInfo.getMissingDefinition(
                    classReference -> missingClasses.put(classReference, missingDefinitionInfo),
                    emptyConsumer(),
                    emptyConsumer()));
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
      ClassReference classReference, MissingDefinitionContext... expectedContexts) {
    return assertIsMissingClassWithExactContexts(classReference, Arrays.asList(expectedContexts));
  }

  public FoundMissingDefinitionsDiagnosticSubject assertIsMissingClassWithExactContexts(
      ClassReference classReference, List<MissingDefinitionContext> expectedContexts) {
    return inspectMissingClassInfo(
        classReference,
        missingClassInfoSubject -> missingClassInfoSubject.assertExactContexts(expectedContexts));
  }

  public FoundMissingDefinitionsDiagnosticSubject assertNumberOfMissingClasses(int expected) {
    assertEquals(expected, getDiagnostic().getMissingDefinitions().size());
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
