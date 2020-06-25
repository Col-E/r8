// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.shaking.ProguardConfigurationSourceStrings.createConfigurationForTesting;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CallGraph.Node;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfigurationParser;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.junit.Test;

public class PartialCallGraphTest extends CallGraphTestBase {
  private final AppView<AppInfoWithLiveness> appView;
  private final InternalOptions options;
  private final ExecutorService executorService;

  public PartialCallGraphTest() throws Exception {
    AndroidApp app = testForD8().addProgramClasses(TestClass.class).compile().app;
    this.appView =
        computeAppViewWithLiveness(
            app,
            factory -> {
              ProguardConfigurationParser parser =
                  new ProguardConfigurationParser(factory, new Reporter());
              parser.parse(
                  createConfigurationForTesting(
                      ImmutableList.of("-keep class ** { void m1(); void m5(); }")));
              return parser.getConfig();
            });
    this.options = appView.options();
    this.executorService = ThreadUtils.getExecutorService(options);
  }

  @Test
  public void testFullGraph() throws Exception {
    CallGraph cg = new CallGraphBuilder(appView).build(executorService, Timing.empty());
    Node m1 = findNode(cg.nodes, "m1");
    Node m2 = findNode(cg.nodes, "m2");
    Node m3 = findNode(cg.nodes, "m3");
    Node m4 = findNode(cg.nodes, "m4");
    Node m5 = findNode(cg.nodes, "m5");
    Node m6 = findNode(cg.nodes, "m6");
    assertNotNull(m1);
    assertNotNull(m2);
    assertNotNull(m3);
    assertNotNull(m4);
    assertNotNull(m5);
    assertNotNull(m6);

    Set<DexEncodedMethod> wave = cg.extractLeaves().toDefinitionSet();
    assertEquals(4, wave.size()); // including <init>
    assertThat(wave, hasItem(m3.getMethod()));
    assertThat(wave, hasItem(m4.getMethod()));
    assertThat(wave, hasItem(m6.getMethod()));

    wave = cg.extractLeaves().toDefinitionSet();
    assertEquals(2, wave.size());
    assertThat(wave, hasItem(m2.getMethod()));
    assertThat(wave, hasItem(m5.getMethod()));

    wave = cg.extractLeaves().toDefinitionSet();
    assertEquals(1, wave.size());
    assertThat(wave, hasItem(m1.getMethod()));
    assertTrue(cg.nodes.isEmpty());
  }

  @Test
  public void testPartialGraph() throws Exception {
    ProgramMethod em1 = findMethod("m1");
    ProgramMethod em2 = findMethod("m2");
    ProgramMethod em4 = findMethod("m4");
    ProgramMethod em5 = findMethod("m5");
    assertNotNull(em1);
    assertNotNull(em2);
    assertNotNull(em4);
    assertNotNull(em5);

    ProgramMethodSet seeds = ProgramMethodSet.create();
    seeds.add(em1);
    seeds.add(em2);
    seeds.add(em4);
    seeds.add(em5);
    CallGraph pg =
        new PartialCallGraphBuilder(appView, seeds).build(executorService, Timing.empty());

    Node m1 = findNode(pg.nodes, "m1");
    Node m2 = findNode(pg.nodes, "m2");
    Node m4 = findNode(pg.nodes, "m4");
    Node m5 = findNode(pg.nodes, "m5");
    assertNotNull(m1);
    assertNotNull(m2);
    assertNotNull(m4);
    assertNotNull(m5);

    Set<DexEncodedMethod> wave = Sets.newIdentityHashSet();

    wave.addAll(pg.extractRoots().toDefinitionSet());
    assertEquals(2, wave.size());
    assertThat(wave, hasItem(m1.getMethod()));
    assertThat(wave, hasItem(m5.getMethod()));
    wave.clear();

    wave.addAll(pg.extractRoots().toDefinitionSet());
    assertEquals(1, wave.size());
    assertThat(wave, hasItem(m2.getMethod()));
    wave.clear();

    wave.addAll(pg.extractRoots().toDefinitionSet());
    assertEquals(1, wave.size());
    assertThat(wave, hasItem(m4.getMethod()));
    assertTrue(pg.nodes.isEmpty());
  }

  private Node findNode(Iterable<Node> nodes, String name) {
    for (Node n : nodes) {
      if (n.getMethod().method.name.toString().equals(name)) {
        return n;
      }
    }
    return null;
  }

  private ProgramMethod findMethod(String name) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.method.name.toString().equals(name)) {
          return new ProgramMethod(clazz, method);
        }
      }
    }
    return null;
  }

  static class TestClass {
    void m1() {
      System.out.println("m1");
      m2();
    }

    void m2() {
      System.out.println("m2");
      m3();
      m4();
    }

    void m3() {
      System.out.println("m3");
    }

    void m4() {
      System.out.println("m4");
    }

    void m5() {
      System.out.println("m5");
      m6();
      m4();
    }

    void m6() {
      System.out.println("m6");
    }
  }
}
