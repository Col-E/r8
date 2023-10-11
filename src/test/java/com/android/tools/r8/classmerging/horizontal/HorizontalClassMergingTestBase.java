// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.horizontal;

import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.horizontalclassmerging.ClassMerger;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public abstract class HorizontalClassMergingTestBase extends TestBase {

  protected final TestParameters parameters;

  protected HorizontalClassMergingTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  protected FieldSubject classMergerClassIdField(ClassSubject classSubject) {
    assertTrue(classSubject.isPresent());
    FieldSubject[] classIds =
        classSubject.allFields().stream()
            .filter(f -> f.getOriginalName().startsWith(ClassMerger.CLASS_ID_FIELD_PREFIX))
            .toArray(FieldSubject[]::new);
    if (classIds.length == 1) {
      return classIds[0];
    }
    if (classIds.length == 0) {
      throw new RuntimeException("No class id field found on " + classSubject);
    }
    throw new RuntimeException("Multiple matching class id fields found on " + classSubject);
  }
}
