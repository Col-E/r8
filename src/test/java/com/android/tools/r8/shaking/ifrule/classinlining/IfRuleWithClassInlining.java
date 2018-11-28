// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.classinlining;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfRuleWithClassInlining extends TestBase {

  private final Backend backend;
  private final boolean enableClassInlining;
  private final boolean enableIfRule;

  public IfRuleWithClassInlining(
      Backend backend, boolean enableClassInlining, boolean enableIfRule) {
    this.backend = backend;
    this.enableClassInlining = enableClassInlining;
    this.enableIfRule = enableIfRule;
  }

  @Parameters(name = "Backend: {0}, class inlining: {1}, with if rule: {2}")
  public static List<Object[]> data() {
    return buildParameters(Backend.values(), BooleanUtils.values(), BooleanUtils.values());
  }

  @Test
  public void r8Test() throws Exception {
    String ifRule =
        StringUtils.lines(
            "-if class " + StringBox.Builder.class.getTypeName(),
            "-keep class " + Unused.class.getTypeName());
    CodeInspector inspector =
        testForR8(backend)
            .addInnerClasses(IfRuleWithClassInlining.class)
            .addKeepMainRule(TestClass.class)
            // TODO(b/120061431): Should not be needed for this example.
            .addKeepRules("-allowaccessmodification")
            .addKeepRules(enableIfRule ? ifRule : "")
            .addOptionsModification(options -> options.enableClassInlining = enableClassInlining)
            .compile()
            .inspector();
    if (enableIfRule || !enableClassInlining) {
      assertThat(inspector.clazz(StringBox.Builder.class), isPresent());
    } else {
      assertThat(inspector.clazz(StringBox.Builder.class), not(isPresent()));
    }
    if (enableIfRule) {
      assertThat(inspector.clazz(Unused.class), isPresent());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      StringBox box = StringBox.builder().setString("Hello world").build();
      System.out.println(box.getString());
    }
  }

  static class StringBox {

    static class Builder {

      private String string = null;

      public Builder setString(String string) {
        this.string = string;
        return this;
      }

      public StringBox build() {
        return new StringBox(string);
      }
    }

    private final String string;

    public StringBox(String string) {
      this.string = string;
    }

    public String getString() {
      return string;
    }

    public static Builder builder() {
      return new Builder();
    }
  }

  static class Unused {}
}
