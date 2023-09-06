// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.structural;

import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

/**
 * This is an interface that mimics the Hasher interface in Guava which allows us to use hashing in
 * the tests.
 */
public interface HasherWrapper {

  void putBoolean(boolean value);

  void putInt(int value);

  void putFloat(float value);

  void putLong(long value);

  void putDouble(double value);

  void putBytes(byte[] content);

  String hashCodeAsString();

  @SuppressWarnings("TypeParameterUnusedInFormals")
  <T> T hash();

  static HasherWrapper sha256Hasher() {
    return new HasherWrapped(Hashing.sha256().newHasher());
  }

  static HasherWrapper murmur3128Hasher() {
    return new HasherWrapped(Hashing.murmur3_128().newHasher());
  }

  class HasherWrapped implements HasherWrapper {

    private final Hasher hasher;

    public HasherWrapped(Hasher hasher) {
      this.hasher = hasher;
    }

    @Override
    public void putBoolean(boolean value) {
      hasher.putBoolean(value);
    }

    @Override
    public void putInt(int value) {
      hasher.putInt(value);
    }

    @Override
    public void putFloat(float value) {
      hasher.putFloat(value);
    }

    @Override
    public void putLong(long value) {
      hasher.putLong(value);
    }

    @Override
    public void putDouble(double value) {
      hasher.putDouble(value);
    }

    @Override
    public void putBytes(byte[] content) {
      hasher.putBytes(content);
    }

    @Override
    @SuppressWarnings({"TypeParameterUnusedInFormals", "unchecked"})
    public <T> T hash() {
      return (T) hasher.hash();
    }

    @Override
    public String hashCodeAsString() {
      return hasher.hash().toString();
    }
  }
}
