package com.android.tools.r8.regress.b142682636;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.code.MoveWide;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;

public class Regress142682636Runner extends TestBase {
  private final Class<?> testClass = Regress142682636.class;

  @Test
  public void test() throws Exception {
    CodeInspector inspector = testForD8()
        .addProgramClasses(testClass)
        .release()
        .compile()
        .inspector();
    ClassSubject clazz = inspector.clazz(testClass);
    assertThat(clazz, isPresent());
    MethodSubject foo = clazz.uniqueMethodWithName("foo");
    assertThat(foo, isPresent());
    checkNoMoveWide(foo);
    MethodSubject bar = clazz.uniqueMethodWithName("bar");
    assertThat(bar, isPresent());
    checkNoMoveWide(bar);
  }

  private void checkNoMoveWide(MethodSubject m) {
    assertTrue(Arrays.stream(m.getMethod().getCode().asDexCode().instructions)
        .noneMatch(i -> i instanceof MoveWide));
  }

}
