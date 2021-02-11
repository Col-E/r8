// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.ir.desugar.LambdaClass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;

public class ClassConverterResult {

  private final List<LambdaClass> synthesizedLambdaClassesWithDeterministicOrder;

  private ClassConverterResult(List<LambdaClass> synthesizedLambdaClassesWithDeterministicOrder) {
    this.synthesizedLambdaClassesWithDeterministicOrder =
        synthesizedLambdaClassesWithDeterministicOrder;
  }

  public static Builder builder() {
    return new Builder();
  }

  public void forEachSynthesizedLambdaClassWithDeterministicOrdering(
      Consumer<LambdaClass> consumer) {
    synthesizedLambdaClassesWithDeterministicOrder.forEach(consumer);
  }

  public List<LambdaClass> getSynthesizedLambdaClasses() {
    return synthesizedLambdaClassesWithDeterministicOrder;
  }

  public static class Builder {

    private final List<LambdaClass> synthesizedLambdaClasses = new ArrayList<>();

    public Builder addSynthesizedLambdaClass(LambdaClass lambdaClass) {
      synchronized (synthesizedLambdaClasses) {
        synthesizedLambdaClasses.add(lambdaClass);
      }
      return this;
    }

    public ClassConverterResult build() {
      synthesizedLambdaClasses.sort(Comparator.comparing(LambdaClass::getType));
      return new ClassConverterResult(synthesizedLambdaClasses);
    }
  }
}
