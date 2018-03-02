// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package lambdas.kstyle.captures

fun consume(l: () -> String) = l()

fun main(args: Array<String>) {
    test()
}

private fun test() {
    test1(1, 2, 3, "A", "B", "C", D("x"), D("y"), D("z"), 7, 8, 9)
    test2(true, 10, '*', 20, 30, 40, 50.0f, 60.0, D("D"), "S", null, 70)
}

data class D(val d: String)

private fun test1(
        i1: Int, i2: Int, i3: Int,
        s1: String, s2: String, s3: String,
        d1: D, d2: D, d3: D,
        o1: Int?, o2: Int?, o3: Int?
) {
    println(consume { "a: $i1 $i2 $i3" })
    println(consume { "b: $i2 $i3 $i1" })
    println(consume { "c: $i3 $i1 $i2" })

    println(consume { "d: $i1 $s1 $d1" })
    println(consume { "e: $i2 $d2 $s2" })
    println(consume { "f: $i3 $d3 $d1" })
    println(consume { "g: $o1 $d3 $i3" })
    println(consume { "h: $o2 $o3 $i1" })

    println(consume { "i: $s1 $s2 $s3" })
    println(consume { "j: $d1 $d2 $d3" })
    println(consume { "k: $o1 $o2 $o3" })
    println(consume { "l: $s1 $d2 $o3" })
    println(consume { "n: $o1 $s2 $d3" })
    println(consume { "o: $d1 $o2 $s3" })

    println(consume { "p: $i1 $i2 $s3" })
}

private fun test2(
        z: Boolean, b: Byte, c: Char, s: Short,
        i: Int, l: Long, f: Float, d: Double,
        o1: D, o2: String, o3: Any?, o4: Byte?
) {
    println(consume { "a: $z $b $c $s $i $l $f $d $o1 $o2 $o3 $o4" })
    println(consume { "a: $z $b $o1 $o2 $c $s $i $l $f $d $o3 $o4" })
    println(consume { "a: $z $c $s $l $f $d $o2 $o3 $o4 $b $i $o1" })
    println(consume { "a: $o1 $o2 $o3 $o4 $z $b $c $s $i $l $f $d" })

    println(consume { "a: $z $b $c $s $i $l $f $d $o1 $o2 \$o3 \$o4" })
    println(consume { "a: $z $b $c $s $i $l $f $d $o1 \$o2 \$o3 $o4" })
    println(consume { "a: $z $b $c $s $i $l $f $d \$o1 \$o2 $o3 $o4" })
    println(consume { "a: $z $b $c $s $i $l $f $d \$o1 $o2 $o3 \$o4" })

    println(consume { "x: $z $b $c $s $i $l $f $d $o1 $o2 \$o3 $o4" })
    println(consume { "y: $z $b $c $s $i $l \$f $d $o1 $o2 $o3 $o4" })
    println(consume { "z: $z $b $c \$s $i $l $f $d $o1 $o2 $o3 $o4" })
}

