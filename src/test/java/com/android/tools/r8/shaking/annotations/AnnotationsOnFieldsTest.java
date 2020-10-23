package com.android.tools.r8.shaking.annotations;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.AssumeMayHaveSideEffects;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AnnotationsOnFieldsTest extends TestBase {

  private static final List<Class<?>> CLASSES =
      ImmutableList.of(
          FieldAnnotation.class,
          StaticFieldAnnotation.class,
          FieldAnnotationUse.class,
          StaticFieldAnnotationUse.class,
          TestClass.class,
          MainClass.class);

  private final Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return ToolHelper.getBackends();
  }

  public AnnotationsOnFieldsTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    testForR8Compat(backend)
        .enableNeverClassInliningAnnotations()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MainClass.class)
        .addKeepRules(
            "-keep @interface **.*Annotation { *; }",
            "-keepclassmembers class * { @**.*Annotation <fields>; }",
            "-keepattributes *Annotation*")
        .enableSideEffectAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(TestClass.class);
              assertThat(clazz, isPresent());
              assertThat(clazz, isPresentAndRenamed());

              FieldSubject field = clazz.uniqueFieldWithName("field");
              assertThat(field, isPresent());
              assertThat(field.annotation(FieldAnnotation.class.getTypeName()), isPresent());
              assertThat(inspector.clazz(FieldAnnotationUse.class), isPresentAndRenamed());

              FieldSubject staticField = clazz.uniqueFieldWithName("staticField");
              assertThat(staticField, isPresent());
              assertThat(
                  staticField.annotation(StaticFieldAnnotation.class.getTypeName()), isPresent());
              assertThat(inspector.clazz(StaticFieldAnnotationUse.class), isPresentAndRenamed());
            })
        .run(MainClass.class)
        .assertSuccess();
  }

}

@Target(FIELD)
@Retention(RUNTIME)
@interface FieldAnnotation {
  Class<?> clazz();
}

@Target(FIELD)
@Retention(RUNTIME)
@interface StaticFieldAnnotation {
  Class<?> clazz();
}

class FieldAnnotationUse {}

@NoHorizontalClassMerging
class StaticFieldAnnotationUse {}

@NeverClassInline
class TestClass {

  @AssumeMayHaveSideEffects
  public TestClass() {}

  @StaticFieldAnnotation(clazz = StaticFieldAnnotationUse.class)
  static int staticField;

  @FieldAnnotation(clazz = FieldAnnotationUse.class)
  int field;
}

class MainClass {

  public static void main(String[] args) {
    new TestClass();
  }
}