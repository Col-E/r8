// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Box;

public class FoundMissingDefinitionContextSubject {

  private final MissingDefinitionContext context;

  public FoundMissingDefinitionContextSubject(MissingDefinitionContext context) {
    this.context = context;
  }

  public FoundMissingDefinitionContextSubject assertEqualTo(
      MissingDefinitionContext expectedContext) {
    Box<ClassReference> classReference = new Box<>();
    Box<FieldReference> fieldReference = new Box<>();
    Box<MethodReference> methodReference = new Box<>();
    context.getReference(classReference::set, fieldReference::set, methodReference::set);
    Box<ClassReference> expectedClassReference = new Box<>();
    Box<FieldReference> expectedFieldReference = new Box<>();
    Box<MethodReference> expectedMethodReference = new Box<>();
    expectedContext.getReference(
        expectedClassReference::set, expectedFieldReference::set, expectedMethodReference::set);
    assertEquals(classReference, expectedClassReference);
    assertEquals(fieldReference, expectedFieldReference);
    assertEquals(methodReference, expectedMethodReference);
    return this;
  }
}
