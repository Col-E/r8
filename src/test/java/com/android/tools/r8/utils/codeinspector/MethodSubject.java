// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.google.common.collect.Streams;
import java.util.Iterator;
import java.util.function.Predicate;
import java.util.stream.Stream;

public abstract class MethodSubject extends MemberSubject {

  public abstract boolean isAbstract();

  public abstract boolean isBridge();

  public abstract boolean isInstanceInitializer();

  public abstract boolean isClassInitializer();

  public abstract String getOriginalSignatureAttribute();

  public abstract String getFinalSignatureAttribute();

  public abstract DexEncodedMethod getMethod();

  public Iterator<InstructionSubject> iterateInstructions() {
    return null;
  }

  public <T extends InstructionSubject> Iterator<T> iterateInstructions(
      Predicate<InstructionSubject> filter) {
    return null;
  }

  public boolean hasLineNumberTable() {
    return getLineNumberTable() != null;
  }

  public abstract LineNumberTable getLineNumberTable();

  public abstract boolean hasLocalVariableTable();

  public Stream<InstructionSubject> streamInstructions() {
    return Streams.stream(iterateInstructions());
  }

  @Override
  public MethodSubject asMethodSubject() {
    return this;
  }

  @Override
  public boolean isMethodSubject() {
    return true;
  }
}
