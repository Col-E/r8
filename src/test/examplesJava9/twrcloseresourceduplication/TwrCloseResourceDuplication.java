// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package twrcloseresourceduplication;

import java.util.jar.JarFile;

public class TwrCloseResourceDuplication {

  public static class Foo {

    void foo(String name) {
      try (JarFile f = new JarFile(name)) {
        System.out.println("foo opened 1");
      } catch (Exception e) {
        System.out.println("foo caught from 1: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("foo post close 1");
      }
      try (JarFile f = new JarFile(name)) {
        System.out.println("foo opened 2");
        throw new RuntimeException();
      } catch (Exception e) {
        System.out.println("foo caught from 2: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("foo post close 2");
      }
    }
  }

  public static class Bar {

    void bar(String name) {
      try (JarFile f = new JarFile(name)) {
        System.out.println("bar opened 1");
      } catch (Exception e) {
        System.out.println("bar caught from 1: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("bar post close 1");
      }
      try (JarFile f = new JarFile(name)) {
        System.out.println("bar opened 2");
        throw new RuntimeException();
      } catch (Exception e) {
        System.out.println("bar caught from 2: " + e.getClass().getSimpleName());
      } finally {
        System.out.println("bar post close 2");
      }
    }
  }

  public static void main(String[] args) {
    new Foo().foo(args[0]);
    new Bar().bar(args[0]);
  }
}
