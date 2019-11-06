// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.info;

import com.android.tools.r8.graph.DexString;
import java.util.Optional;
import java.util.OptionalLong;

// Simple data class that encapsulates constant values. Usages are:
//   * return value in a method summary when the method returns a constant.
//   * call-site optimization info if a certain method is always invoked with a constant.
//
// Note that this data class mimics C-like union. That is, a constant content that a value can hold
// should be *one* of constant kinds, not multiple things at the same time. E.g., a value cannot
// have ConstNumber and ConstString simultaneously. Content's orthogonality will be checked at
// {@link #hasConstant()}.
class ConstantData {
  private static final ConstantData INSTANCE = new ConstantData();

  static ConstantData getDefaultInstance() {
    return INSTANCE;
  }

  private final OptionalLong constantNumber;
  // TODO(b/69963623): item-based string?
  private final Optional<DexString> constantString;
  // TODO(b/69963623): constantClass?

  private ConstantData() {
    this.constantNumber = OptionalLong.empty();
    this.constantString = Optional.empty();
  }

  private ConstantData(long constantNumber) {
    this.constantNumber = OptionalLong.of(constantNumber);
    this.constantString = Optional.empty();
  }

  private ConstantData(DexString constantString) {
    this.constantNumber = OptionalLong.empty();
    this.constantString = Optional.of(constantString);
  }

  static ConstantData fromConstantNumber(long constantNumber) {
    return new ConstantData(constantNumber);
  }

  static ConstantData fromConstantString(DexString constantString) {
    return new ConstantData(constantString);
  }

  boolean hasConstant() {
    assert !(hasConstantNumber() && hasConstantString());
    return hasConstantNumber() || hasConstantString();
  }

  boolean hasConstantNumber() {
    return constantNumber.isPresent();
  }

  boolean hasConstantString() {
    return constantString.isPresent();
  }

  long getConstantNumber() {
    assert hasConstantNumber() && !hasConstantString();
    return constantNumber.getAsLong();
  }

  DexString getConstantString() {
    assert !hasConstantNumber() && hasConstantString();
    return constantString.get();
  }
}
