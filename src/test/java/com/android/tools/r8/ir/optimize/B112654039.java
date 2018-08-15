package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Ignore;
import org.junit.Test;

public class B112654039 extends TestBase {
  @Ignore("b/112654039")
  @Test
  public void test() throws Exception {
    AndroidApp app = readClasses(TestClassForB112654039.class);
    AndroidApp processedApp = ToolHelper.runD8(app, options -> {
      // Pretend input .class does not have debugging info.
      options.debug = false;
    });
    CodeInspector inspector = new CodeInspector(processedApp);
    ClassSubject clazz = inspector.clazz(TestClassForB112654039.class);
    assertThat(clazz, isPresent());
  }
}

class TestClassForB112654039 {
  void foo() {
    double[] a = new double[1];
    a[-1] = 1.0;
  }
}
