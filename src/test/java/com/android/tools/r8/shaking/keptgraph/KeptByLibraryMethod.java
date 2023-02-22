// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.graphinspector.GraphInspector;
import java.util.ArrayList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptByLibraryMethod extends TestBase {

  // Library

  public abstract static class Drawable {
    abstract void draw();
  }

  public static class Scene {
    private Collection<Drawable> drawables = new ArrayList<>();

    public void register(Drawable drawable) {
      drawables.add(drawable);
    }

    public void drawScene() {
      for (Drawable drawable : drawables) {
        drawable.draw();
      }
    }
  }

  // Program

  public static class MyShape extends Drawable {

    @Override
    void draw() {
      System.out.println("MyShape was drawn!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Scene scene = new Scene();
      scene.register(new MyShape());
      scene.drawScene();
    }
  }

  // Test runner

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public KeptByLibraryMethod(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    GraphInspector inspector =
        testForR8(parameters.getBackend())
            .enableGraphInspector()
            .addKeepMainRule(Main.class)
            .addLibraryClasses(Scene.class, Drawable.class)
            .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
            .addProgramClasses(Main.class, MyShape.class)
            .compile()
            // The use of classpath classes is unsupported for DEX runtimes currently.
            .addRunClasspathClasses(Scene.class, Drawable.class)
            .run(parameters.getRuntime(), Main.class)
            .assertSuccessWithOutputLines("MyShape was drawn!")
            .graphInspector();

    inspector
        .method(Reference.methodFromMethod(MyShape.class.getDeclaredMethod("draw")))
        .assertPresent()
        .assertKeptByLibraryMethod(inspector.clazz(Reference.classFromClass(MyShape.class)));
  }
}
