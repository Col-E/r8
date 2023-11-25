// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.io.IOException;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress160394262Test extends TestBase {

  static final String EXPECTED = StringUtils.lines("1;2;3");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public Regress160394262Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(Regress160394262Test.class)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .allowAccessModification()
        .addKeepMainRule(TestClass.class)
        .addInnerClasses(Regress160394262Test.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkJoinerIsClassInlined);
  }

  private void checkJoinerIsClassInlined(CodeInspector inspector) {
    assertThat(inspector.clazz(Joiner.class.getTypeName() + "$1"), isAbsent());
    assertThat(inspector.clazz(Joiner.class), isAbsent());
  }

  static class TestClass {

    public void foo(List<?> items) {
      System.out.println(Joiner.on(";").skipNulls().join(items));
    }

    public static void main(String[] args) {
      new TestClass().foo(Arrays.asList("1", 2, 3l, null));
    }
  }

  // Minimized copy of com.google.common.base.Preconditions
  static class Preconditions {

    public static <T> T checkNotNull(T reference) {
      if (reference == null) {
        throw new NullPointerException();
      } else {
        return reference;
      }
    }

    public static <T> T checkNotNull(T reference, Object errorMessage) {
      if (reference == null) {
        throw new NullPointerException(String.valueOf(errorMessage));
      } else {
        return reference;
      }
    }
  }

  // Minimized copy of com.google.common.base.Joiner
  static class Joiner {

    private final String separator;

    public static Joiner on(String separator) {
      return new Joiner(separator);
    }

    private Joiner(String separator) {
      this.separator = Preconditions.checkNotNull(separator);
    }

    private Joiner(Joiner prototype) {
      this.separator = prototype.separator;
    }

    public <A extends Appendable> A appendTo(A appendable, Iterator<?> parts) throws IOException {
      Preconditions.checkNotNull(appendable);
      if (parts.hasNext()) {
        appendable.append(this.toString(parts.next()));
        while (parts.hasNext()) {
          appendable.append(this.separator);
          appendable.append(this.toString(parts.next()));
        }
      }
      return appendable;
    }

    public final String join(Iterable<?> parts) {
      return join(parts.iterator());
    }

    public final String join(Iterator<?> parts) {
      StringBuilder builder = new StringBuilder();
      try {
        appendTo((Appendable) builder, parts);
        return builder.toString();
      } catch (IOException e) {
        throw new AssertionError(e);
      }
    }

    public Joiner skipNulls() {
      return new Joiner(this) {
        public <A extends Appendable> A appendTo(A appendable, Iterator<?> parts)
            throws IOException {
          Preconditions.checkNotNull(appendable, "appendable");
          Preconditions.checkNotNull(parts, "parts");
          Object part;
          while (parts.hasNext()) {
            part = parts.next();
            if (part != null) {
              appendable.append(Joiner.this.toString(part));
              break;
            }
          }
          while (parts.hasNext()) {
            part = parts.next();
            if (part != null) {
              appendable.append(Joiner.this.separator);
              appendable.append(Joiner.this.toString(part));
            }
          }
          return appendable;
        }
      };
    }

    CharSequence toString(Object part) {
      Preconditions.checkNotNull(part);
      return part instanceof CharSequence ? (CharSequence) part : part.toString();
    }
  }
}
