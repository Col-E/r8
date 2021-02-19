// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.diagnosticinspector;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.diagnostic.MissingDefinitionClassContext;
import com.android.tools.r8.diagnostic.MissingDefinitionContext;
import com.android.tools.r8.diagnostic.MissingDefinitionFieldContext;
import com.android.tools.r8.diagnostic.MissingDefinitionMethodContext;

public class FoundMissingDefinitionContextSubject {

  private final MissingDefinitionContext context;

  public FoundMissingDefinitionContextSubject(MissingDefinitionContext context) {
    this.context = context;
  }

  public FoundMissingDefinitionContextSubject assertEqualTo(
      MissingDefinitionClassContext expectedContext) {
    assertTrue(context.isClassContext());
    assertEquals(expectedContext.getClassReference(), context.asClassContext().getClassReference());
    return this;
  }

  public FoundMissingDefinitionContextSubject assertEqualTo(
      MissingDefinitionFieldContext expectedContext) {
    assertTrue(context.isFieldContext());
    assertEquals(expectedContext.getFieldReference(), context.asFieldContext().getFieldReference());
    return this;
  }

  public FoundMissingDefinitionContextSubject assertEqualTo(
      MissingDefinitionMethodContext expectedContext) {
    assertTrue(context.isMethodContext());
    assertEquals(
        expectedContext.getMethodReference(), context.asMethodContext().getMethodReference());
    return this;
  }
}
