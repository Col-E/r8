// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.diagnostic.DefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionInfo;
import com.android.tools.r8.diagnostic.internal.DefinitionContextUtils;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FoundMissingDefinitionInfoSubject {

  private final MissingDefinitionInfo missingDefinitionInfo;

  private final Map<ClassReference, FoundMissingDefinitionContextSubject> classContexts =
      new HashMap<>();
  private final Map<FieldReference, FoundMissingDefinitionContextSubject> fieldContexts =
      new HashMap<>();
  private final Map<MethodReference, FoundMissingDefinitionContextSubject> methodContexts =
      new HashMap<>();

  public FoundMissingDefinitionInfoSubject(MissingDefinitionInfo missingDefinitionInfo) {
    this.missingDefinitionInfo = missingDefinitionInfo;
    missingDefinitionInfo
        .getReferencedFromContexts()
        .forEach(
            context ->
                DefinitionContextUtils.accept(
                    context,
                    classContext ->
                        classContexts.put(
                            classContext.getClassReference(),
                            new FoundMissingDefinitionContextSubject(context)),
                    fieldContext ->
                        fieldContexts.put(
                            fieldContext.getFieldReference(),
                            new FoundMissingDefinitionContextSubject(context)),
                    methodContext ->
                        methodContexts.put(
                            methodContext.getMethodReference(),
                            new FoundMissingDefinitionContextSubject(context))));
  }

  public FoundMissingDefinitionInfoSubject assertExactContexts(
      List<DefinitionContext> expectedContexts) {
    assertEquals(expectedContexts.size(), missingDefinitionInfo.getReferencedFromContexts().size());
    expectedContexts.forEach(
        expectedContext ->
            DefinitionContextUtils.accept(
                expectedContext,
                expectedClassContext -> {
                  FoundMissingDefinitionContextSubject subject =
                      classContexts.get(expectedClassContext.getClassReference());
                  assertNotNull(subject);
                  subject.assertEqualTo(expectedClassContext);
                },
                expectedFieldContext -> {
                  FoundMissingDefinitionContextSubject subject =
                      fieldContexts.get(expectedFieldContext.getFieldReference());
                  assertNotNull(subject);
                  subject.assertEqualTo(expectedFieldContext);
                },
                expectedMethodContext -> {
                  FoundMissingDefinitionContextSubject subject =
                      methodContexts.get(expectedMethodContext.getMethodReference());
                  assertNotNull(subject);
                  subject.assertEqualTo(expectedMethodContext);
                }));
    return this;
  }
}
