// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package autovalue;

import com.google.auto.value.AutoValue;
import javax.annotation.Nullable;

public class SimpleAutoValue {

  @AutoValue
  static abstract class Pair {

    Pair() {
      // Intentionally left empty.
    }

    abstract int getOne();

    @Nullable
    abstract String getOther();

    abstract String getRequiredOther();

    static Builder builder() {
      return new AutoValue_SimpleAutoValue_Pair.Builder();
    }

    @AutoValue.Builder
    abstract static class Builder {

      abstract Builder setOne(int value);

      abstract Builder setOther(String value);

      abstract Builder setRequiredOther(String value);

      abstract Pair build();
    }
  }

  public static void main(String... args) {
    Pair.Builder builder = Pair.builder();
    builder.setOne(42);
    builder.setRequiredOther("123");
    System.out.println(builder.build());
    builder = Pair.builder();
    try {
      builder.build();
    } catch (Exception e) {
      System.out.println(e.getMessage());
    }
  }
}
