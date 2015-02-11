/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package com.oracle.graal.compiler.test;

import static com.oracle.graal.api.code.Assumptions.*;

import org.junit.*;

import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.common.*;
import com.oracle.graal.phases.tiers.*;

public class IntegerEqualsCanonicalizerTest extends GraalCompilerTest {

    @Test
    public void testShiftEquals() {
        /*
         * tests the canonicalization of (x >>> const) == 0 to x |test| (-1 << const)
         */
        test("testShiftEqualsSnippet", "testShiftEqualsReference");
    }

    @SuppressWarnings("unused") private static int field;

    public static void testShiftEqualsSnippet(int x, int[] array, int y) {
        // optimize
        field = (x >>> 10) == 0 ? 1 : 0;
        field = (array.length >> 10) == 0 ? 1 : 0;
        field = (x << 10) == 0 ? 1 : 0;
        // don't optimize
        field = (x >> 10) == 0 ? 1 : 0;
        field = (x >>> y) == 0 ? 1 : 0;
        field = (x >> y) == 0 ? 1 : 0;
        field = (x << y) == 0 ? 1 : 0;
        field = (x >>> y) == 1 ? 1 : 0;
        field = (x >> y) == 1 ? 1 : 0;
        field = (x << y) == 1 ? 1 : 0;
    }

    public static void testShiftEqualsReference(int x, int[] array, int y) {
        field = (x & 0xfffffc00) == 0 ? 1 : 0;
        field = (array.length & 0xfffffc00) == 0 ? 1 : 0;
        field = (x & 0x3fffff) == 0 ? 1 : 0;
        // don't optimize signed right shifts
        field = (x >> 10) == 0 ? 1 : 0;
        // don't optimize no-constant shift amounts
        field = (x >>> y) == 0 ? 1 : 0;
        field = (x >> y) == 0 ? 1 : 0;
        field = (x << y) == 0 ? 1 : 0;
        // don't optimize non-zero comparisons
        field = (x >>> y) == 1 ? 1 : 0;
        field = (x >> y) == 1 ? 1 : 0;
        field = (x << y) == 1 ? 1 : 0;
    }

    @Test
    public void testCompare() {
        test("testCompareSnippet", "testCompareReference");
    }

    public static void testCompareSnippet(int x, int y, int[] array1, int[] array2) {
        int tempX = x;
        int array1Length = array1.length;
        int array2Length = array2.length;
        // optimize
        field = x == tempX ? 1 : 0;
        field = x != tempX ? 1 : 0;
        field = array1Length != (-1 - array2Length) ? 1 : 0;
        field = array1Length == (-1 - array2Length) ? 1 : 0;
        // don't optimize
        field = x == y ? 1 : 0;
        field = array1Length == array2Length ? 1 : 0;
        field = array1Length == (-array2Length) ? 1 : 0;
    }

    public static void testCompareReference(int x, int y, int[] array1, int[] array2) {
        int array1Length = array1.length;
        int array2Length = array2.length;
        // optimize
        field = 1;
        field = 0;
        field = 1;
        field = 0;
        // don't optimize (overlapping value ranges)
        field = x == y ? 1 : 0;
        field = array1Length == array2Length ? 1 : 0;
        field = array1Length == (-array2Length) ? 1 : 0;
    }

    private void test(String snippet, String referenceSnippet) {
        StructuredGraph graph = getCanonicalizedGraph(snippet);
        StructuredGraph referenceGraph = getCanonicalizedGraph(referenceSnippet);
        assertEquals(referenceGraph, graph);
    }

    private StructuredGraph getCanonicalizedGraph(String snippet) {
        StructuredGraph graph = parseEager(snippet, ALLOW_OPTIMISTIC_ASSUMPTIONS);
        new CanonicalizerPhase(false).apply(graph, new PhaseContext(getProviders()));
        for (FrameState state : graph.getNodes(FrameState.class).snapshot()) {
            state.replaceAtUsages(null);
            state.safeDelete();
        }
        return graph;
    }
}
