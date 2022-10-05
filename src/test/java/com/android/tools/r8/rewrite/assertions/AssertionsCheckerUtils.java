// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers;
import org.hamcrest.MatcherAssert;
import org.junit.Assert;

public class AssertionsCheckerUtils {

  static void checkAssertionCodeEnabled(ClassSubject subject, String methodName) {
    MatcherAssert.assertThat(subject, Matchers.isPresent());
    // <clinit> is removed by R8.
    if (subject.uniqueMethodWithOriginalName("<clinit>").isPresent()) {
      Assert.assertFalse(
          subject
              .uniqueMethodWithOriginalName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isStaticPut));
    }
    Assert.assertTrue(
        subject
            .uniqueMethodWithOriginalName(methodName)
            .streamInstructions()
            .anyMatch(InstructionSubject::isThrow));
  }

  private AssertionsCheckerUtils() {}
}
