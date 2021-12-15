// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.callgraph;

import com.android.tools.r8.graph.ProgramMethod;
import java.util.Set;
import java.util.TreeSet;

public class Node extends NodeBase<Node> implements Comparable<Node> {

  public static Node[] EMPTY_ARRAY = {};

  private int numberOfCallSites = 0;

  // Outgoing calls from this method.
  private final Set<Node> callees = new TreeSet<>();

  // Incoming calls to this method.
  private final Set<Node> callers = new TreeSet<>();

  // Incoming field read edges to this method (i.e., the set of methods that read a field written
  // by the current method).
  private final Set<Node> readers = new TreeSet<>();

  // Outgoing field read edges from this method (i.e., the set of methods that write a field read
  // by the current method).
  private final Set<Node> writers = new TreeSet<>();

  public Node(ProgramMethod method) {
    super(method);
  }

  public void addCallerConcurrently(Node caller) {
    addCallerConcurrently(caller, false);
  }

  @Override
  public void addCallerConcurrently(Node caller, boolean likelySpuriousCallEdge) {
    if (caller != this && !likelySpuriousCallEdge) {
      boolean changedCallers;
      synchronized (callers) {
        changedCallers = callers.add(caller);
        numberOfCallSites++;
      }
      if (changedCallers) {
        synchronized (caller.callees) {
          caller.callees.add(this);
        }
        // Avoid redundant field read edges (call edges are considered stronger).
        removeReaderConcurrently(caller);
      }
    } else {
      synchronized (callers) {
        numberOfCallSites++;
      }
    }
  }

  @Override
  public void addReaderConcurrently(Node reader) {
    if (reader != this) {
      synchronized (callers) {
        if (callers.contains(reader)) {
          // Avoid redundant field read edges (call edges are considered stronger).
          return;
        }
        boolean readersChanged;
        synchronized (readers) {
          readersChanged = readers.add(reader);
        }
        if (readersChanged) {
          synchronized (reader.writers) {
            reader.writers.add(this);
          }
        }
      }
    }
  }

  private void removeReaderConcurrently(Node reader) {
    synchronized (readers) {
      readers.remove(reader);
    }
    synchronized (reader.writers) {
      reader.writers.remove(this);
    }
  }

  public void removeCaller(Node caller) {
    boolean callersChanged = callers.remove(caller);
    assert callersChanged;
    boolean calleesChanged = caller.callees.remove(this);
    assert calleesChanged;
    assert !hasReader(caller);
  }

  public void removeReader(Node reader) {
    boolean readersChanged = readers.remove(reader);
    assert readersChanged;
    boolean writersChanged = reader.writers.remove(this);
    assert writersChanged;
    assert !hasCaller(reader);
  }

  public void cleanCalleesAndWritersForRemoval() {
    assert callers.isEmpty();
    assert readers.isEmpty();
    for (Node callee : callees) {
      boolean changed = callee.callers.remove(this);
      assert changed;
    }
    for (Node writer : writers) {
      boolean changed = writer.readers.remove(this);
      assert changed;
    }
  }

  public void cleanCallersAndReadersForRemoval() {
    assert callees.isEmpty();
    assert writers.isEmpty();
    for (Node caller : callers) {
      boolean changed = caller.callees.remove(this);
      assert changed;
    }
    for (Node reader : readers) {
      boolean changed = reader.writers.remove(this);
      assert changed;
    }
  }

  public Set<Node> getCallersWithDeterministicOrder() {
    return callers;
  }

  public Set<Node> getCalleesWithDeterministicOrder() {
    return callees;
  }

  public Set<Node> getReadersWithDeterministicOrder() {
    return readers;
  }

  public Set<Node> getWritersWithDeterministicOrder() {
    return writers;
  }

  public int getNumberOfCallSites() {
    return numberOfCallSites;
  }

  public boolean hasCallee(Node method) {
    return callees.contains(method);
  }

  public boolean hasCaller(Node method) {
    return callers.contains(method);
  }

  public boolean hasReader(Node method) {
    return readers.contains(method);
  }

  public boolean hasWriter(Node method) {
    return writers.contains(method);
  }

  public boolean isRoot() {
    return callers.isEmpty() && readers.isEmpty();
  }

  public boolean isLeaf() {
    return callees.isEmpty() && writers.isEmpty();
  }

  @Override
  public int compareTo(Node other) {
    return getProgramMethod().getReference().compareTo(other.getProgramMethod().getReference());
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("MethodNode for: ");
    builder.append(getProgramMethod().toSourceString());
    builder.append(" (");
    builder.append(callees.size());
    builder.append(" callees, ");
    builder.append(callers.size());
    builder.append(" callers");
    builder.append(", invoke count ").append(numberOfCallSites);
    builder.append(").");
    builder.append(System.lineSeparator());
    if (callees.size() > 0) {
      builder.append("Callees:");
      builder.append(System.lineSeparator());
      for (Node call : callees) {
        builder.append("  ");
        builder.append(call.getProgramMethod().toSourceString());
        builder.append(System.lineSeparator());
      }
    }
    if (callers.size() > 0) {
      builder.append("Callers:");
      builder.append(System.lineSeparator());
      for (Node caller : callers) {
        builder.append("  ");
        builder.append(caller.getProgramMethod().toSourceString());
        builder.append(System.lineSeparator());
      }
    }
    return builder.toString();
  }
}
