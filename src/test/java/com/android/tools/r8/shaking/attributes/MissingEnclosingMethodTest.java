// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.attributes;

import com.android.tools.r8.R8CompatTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class DataClass {
  public final int f1;
  public final String f2;

  private DataClass(Builder builder) {
    this.f1 = builder.f1;
    this.f2 = builder.f2;
  }

  @Override
  public String toString() {
    return "f1: " + f1 + ", f2: " + f2;
  }

  public static class Builder {
    private int f1;
    private String f2;

    public Builder() {
    }

    public Builder setF1(int f1) {
      this.f1 = f1;
      return this;
    }

    public Builder setF2(String f2) {
      this.f2 = f2;
      return this;
    }

    public DataClass build() {
      return new DataClass(this);
    }
  }
}

class DataClassUser {
  public static void main(String... args) {
    DataClass.Builder builder = new DataClass.Builder();
    builder.setF1(8);
    builder.setF2("8");
    DataClass d = builder.build();
    System.out.println(d);
  }
}

@RunWith(Parameterized.class)
public class MissingEnclosingMethodTest extends TestBase {
  private static final String EXPECTED_OUTPUT = StringUtils.lines("f1: 8, f2: 8");

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public TestConfig config;

  @Parameter(2)
  public boolean enableMinification;

  enum TestConfig {
    CLASS,
    DUMP_18,
    DUMP_16;

    public void addInnerClasses(R8CompatTestBuilder builder) throws IOException {
      switch (this) {
        case CLASS:
          builder.addInnerClasses(DataClass.class);
          break;
        case DUMP_18:
          builder.addProgramClasses(DataClass.Builder.class);
          builder.addProgramClassFileData(DataClassDumps.dump18());
          break;
        case DUMP_16:
          builder.addProgramClasses(DataClass.Builder.class);
          builder.addProgramClassFileData(DataClassDumps.dump16());
          break;
        default:
          throw new Unreachable();
      }
    }
  }

  @Parameters(name = "{0} {1}  minification: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        TestConfig.values(),
        BooleanUtils.values());
  }

  @Test
  public void b131210377() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addProgramClasses(DataClass.class, DataClassUser.class)
        .apply(config::addInnerClasses)
        .addKeepMainRule(DataClassUser.class)
        .addKeepAttributes("Signature", "InnerClasses", "EnclosingMethod")
        .minification(enableMinification)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), DataClassUser.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }
}
