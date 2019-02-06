// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf_segments;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Metrics {

  public static class SegmentInfo {
    public final String name;
    public final boolean contributeToSize;
    private long items;
    private long size;

    public SegmentInfo(String name) {
      this.name = name;
      this.contributeToSize = true;
      this.items = 0;
      this.size = 0;
    }

    public SegmentInfo(String name, boolean contributeToSize) {
      this.name = name;
      this.contributeToSize = contributeToSize;
      this.items = 0;
      this.size = 0;
    }

    public SegmentInfo increment(long items, long size) {
      this.items += items;
      this.size += size;
      return this;
    }

    public SegmentInfo increment(SegmentInfo otherInfo) {
      assert name == otherInfo.name;
      this.items += otherInfo.items;
      this.size += otherInfo.size;
      return this;
    }

    public long getSize() {
      return size;
    }
  }

  public final SegmentInfo bootstrapMethodAttributes = new SegmentInfo("BootstrapMethodAttributes");
  public final SegmentInfo classFile = new SegmentInfo("ClassFile");
  public final SegmentInfo code = new SegmentInfo("Code");
  public final SegmentInfo constantPool = new SegmentInfo("ConstantPool");
  public final SegmentInfo exceptionTable = new SegmentInfo("ExceptionTable");
  public final SegmentInfo fieldInfo = new SegmentInfo("FieldInfo");
  public final SegmentInfo innerClasses = new SegmentInfo("InnerClasses");
  public final SegmentInfo interfaces = new SegmentInfo("Interfaces");
  public final SegmentInfo otherClassAttributes = new SegmentInfo("OtherClassAttributes");
  public final SegmentInfo lineNumberTableEntries = new SegmentInfo("LineNumberTable");
  public final SegmentInfo localVariableTable = new SegmentInfo("LocalVariableTable");
  public final SegmentInfo loads = new SegmentInfo("Loads", false);
  public final SegmentInfo jumps = new SegmentInfo("Jumps", false);
  public final SegmentInfo maxLocals = new SegmentInfo("MaxLocal", false);
  public final SegmentInfo maxStacks = new SegmentInfo("MaxStack", false);
  public final SegmentInfo methodInfo = new SegmentInfo("Method");
  public final SegmentInfo size = new SegmentInfo("Total").increment(1, 0);
  public final SegmentInfo stores = new SegmentInfo("Stores", false);
  public final SegmentInfo stackMapTable = new SegmentInfo("StackMapTable");
  public final SegmentInfo stackmapTableOtherEntries = new SegmentInfo("StackMapTableOtherEntries");
  public final SegmentInfo stackMapTableFullFrameEntries = new SegmentInfo("StackMapFullFrame");

  public List<SegmentInfo> asList() {
    return new ArrayList(
        Arrays.asList(
            size,
            classFile,
            constantPool,
            interfaces,
            innerClasses,
            bootstrapMethodAttributes,
            otherClassAttributes,
            fieldInfo,
            methodInfo,
            code,
            exceptionTable,
            stackMapTable,
            stackmapTableOtherEntries,
            stackMapTableFullFrameEntries,
            lineNumberTableEntries,
            localVariableTable,
            loads,
            jumps,
            stores,
            maxLocals,
            maxStacks));
  }

  public void increment(Metrics otherMetrics) {
    List<SegmentInfo> otherList = otherMetrics.asList();
    List<SegmentInfo> thisList = asList();
    for (int i = 0; i < thisList.size(); i++) {
      thisList.get(i).increment(otherList.get(i));
    }
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    asList().forEach(s -> builder.append("- " + s.name + ": " + s.size + " / " + s.items + "\n"));
    return builder.toString();
  }
}
