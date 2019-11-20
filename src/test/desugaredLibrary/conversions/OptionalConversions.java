// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package java.util;

public class OptionalConversions {

  private OptionalConversions() {}

  public static <T> j$.util.Optional<T> convert(java.util.Optional<T> optional) {
    if (optional == null) {
      return null;
    }
    if (optional.isPresent()) {
      return j$.util.Optional.of(optional.get());
    }
    return j$.util.Optional.empty();
  }

  public static <T> java.util.Optional<T> convert(j$.util.Optional<T> optional) {
    if (optional == null) {
      return null;
    }
    if (optional.isPresent()) {
      return java.util.Optional.of(optional.get());
    }
    return java.util.Optional.empty();
  }

  public static j$.util.OptionalDouble convert(java.util.OptionalDouble optionalDouble) {
    if (optionalDouble == null) {
      return null;
    }
    if (optionalDouble.isPresent()) {
      return j$.util.OptionalDouble.of(optionalDouble.getAsDouble());
    }
    return j$.util.OptionalDouble.empty();
  }

  public static java.util.OptionalDouble convert(j$.util.OptionalDouble optionalDouble) {
    if (optionalDouble == null) {
      return null;
    }
    if (optionalDouble.isPresent()) {
      return java.util.OptionalDouble.of(optionalDouble.getAsDouble());
    }
    return java.util.OptionalDouble.empty();
  }

  public static j$.util.OptionalLong convert(java.util.OptionalLong optionalLong) {
    if (optionalLong == null) {
      return null;
    }
    if (optionalLong.isPresent()) {
      return j$.util.OptionalLong.of(optionalLong.getAsLong());
    }
    return j$.util.OptionalLong.empty();
  }

  public static java.util.OptionalLong convert(j$.util.OptionalLong optionalLong) {
    if (optionalLong == null) {
      return null;
    }
    if (optionalLong.isPresent()) {
      return java.util.OptionalLong.of(optionalLong.getAsLong());
    }
    return java.util.OptionalLong.empty();
  }

  public static j$.util.OptionalInt convert(java.util.OptionalInt optionalInt) {
    if (optionalInt == null) {
      return null;
    }
    if (optionalInt.isPresent()) {
      return j$.util.OptionalInt.of(optionalInt.getAsInt());
    }
    return j$.util.OptionalInt.empty();
  }

  public static java.util.OptionalInt convert(j$.util.OptionalInt optionalInt) {
    if (optionalInt == null) {
      return null;
    }
    if (optionalInt.isPresent()) {
      return java.util.OptionalInt.of(optionalInt.getAsInt());
    }
    return java.util.OptionalInt.empty();
  }
}
