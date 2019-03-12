package com.android.tools.r8.shaking.annotations;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.util.List;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import static org.junit.Assert.assertThat;

@RunWith(Parameterized.class)
public class AnnotationsOnFieldsTest extends TestBase {

  private final Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public AnnotationsOnFieldsTest(Backend backend) {
    this.backend = backend;
  }

  List<Class<?>> CLASSES = ImmutableList.of(
      FieldAnnotation.class,
      StaticFieldAnnotation.class,
      FieldAnnotationUse.class,
      StaticFieldAnnotationUse.class,
      TestClass.class,
      MainClass.class
  );

  @Test
  public void test() throws Exception {
    testForR8Compat(backend)
        .enableClassInliningAnnotations()
        .addProgramClasses(CLASSES)
        .addKeepMainRule(MainClass.class)
        .addKeepRules("-keep @interface **.*Annotation { *; }")
        .addKeepRules("-keepclassmembers class * { @**.*Annotation <fields>; }")
        .addKeepRules("-keepattributes *Annotation*")
        .compile()
        .inspect(inspector -> {
          ClassSubject clazz = inspector.clazz(TestClass.class);
          assertThat(clazz, isRenamed());

          FieldSubject field = clazz.uniqueFieldWithName("field");
          assertThat(field, isPresent());
          assertThat(field.annotation(FieldAnnotation.class.getTypeName()), isPresent());
          assertThat(inspector.clazz(FieldAnnotationUse.class), isRenamed());

          FieldSubject staticField = clazz.uniqueFieldWithName("staticField");
          assertThat(staticField, isPresent());
          assertThat(staticField.annotation(StaticFieldAnnotation.class.getTypeName()), isPresent());
          assertThat(inspector.clazz(StaticFieldAnnotationUse.class), isRenamed());
        })
        .run(TestClass.class)
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
class StaticFieldAnnotationUse {}

@NeverClassInline
class TestClass {

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