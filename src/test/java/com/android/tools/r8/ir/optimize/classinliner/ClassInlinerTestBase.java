// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.FieldAccessInstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.codeinspector.NewInstanceInstructionSubject;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public abstract class ClassInlinerTestBase extends TestBase {

  protected Set<String> collectTypes(MethodSubject methodSubject) {
    assertNotNull(methodSubject);
    assertThat(methodSubject, isPresent());
    return Stream.concat(
            collectNewInstanceTypesWithRetValue(methodSubject),
            collectStaticGetTypesWithRetValue(methodSubject))
        .collect(Collectors.toSet());
  }

  private Stream<String> collectNewInstanceTypesWithRetValue(MethodSubject methodSubject) {
    return methodSubject
        .streamInstructions()
        .filter(InstructionSubject::isNewInstance)
        .map(is -> ((NewInstanceInstructionSubject) is).getType().toSourceString());
  }

  private Stream<String> collectStaticGetTypesWithRetValue(MethodSubject methodSubject) {
    return methodSubject
        .streamInstructions()
        .filter(InstructionSubject::isStaticGet)
        .map(is -> (FieldAccessInstructionSubject) is)
        .filter(fais -> fais.holder().is(fais.type()))
        .map(fais -> fais.holder().toString());
  }
}
