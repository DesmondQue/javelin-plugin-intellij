package com.javelin.plugin.model;

import com.javelin.plugin.bridge.CoreProcessResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationResultTest {

    // ── StatementResult ───────────────────────────────────────────────────

    @Test
    void statementResultFieldsAreAccessible() {
        var r = new StatementResult("com.example.Foo", 42, 0.707107, 1.0);
        assertEquals("com.example.Foo", r.fullyQualifiedClass());
        assertEquals(42, r.lineNumber());
        assertEquals(0.707107, r.score(), 1e-9);
        assertEquals(1.0, r.rank(), 1e-9);
    }

    @Test
    void statementResultEqualityIsStructural() {
        var a = new StatementResult("com.example.Foo", 42, 0.5, 1.0);
        var b = new StatementResult("com.example.Foo", 42, 0.5, 1.0);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void statementResultInequalityOnEachField() {
        var a = new StatementResult("com.example.Foo", 42, 0.5, 1.0);

        assertNotEquals(a, new StatementResult("com.example.Bar", 42, 0.5, 1.0));
        assertNotEquals(a, new StatementResult("com.example.Foo", 43, 0.5, 1.0));
        assertNotEquals(a, new StatementResult("com.example.Foo", 42, 0.6, 1.0));
        assertNotEquals(a, new StatementResult("com.example.Foo", 42, 0.5, 2.0));
    }

    @Test
    void statementResultToStringContainsAllFields() {
        var r = new StatementResult("com.example.Foo", 42, 0.707, 1.0);
        String str = r.toString();
        assertTrue(str.contains("com.example.Foo"));
        assertTrue(str.contains("42"));
        assertTrue(str.contains("0.707"));
        assertTrue(str.contains("1.0"));
    }

    @Test
    void statementResultImplementsLocalizationResult() {
        LocalizationResult r = new StatementResult("com.example.X", 1, 0.5, 1.0);
        assertEquals("com.example.X", r.fullyQualifiedClass());
        assertEquals(0.5, r.score(), 1e-9);
        assertEquals(1.0, r.rank(), 1e-9);
    }

    // ── MethodResult ──────────────────────────────────────────────────────

    @Test
    void methodResultFieldsAreAccessible() {
        var r = new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 1.5, 10, 25);
        assertEquals("com.example.Foo", r.fullyQualifiedClass());
        assertEquals("doWork", r.methodName());
        assertEquals("(I)V", r.descriptor());
        assertEquals(0.9, r.score(), 1e-9);
        assertEquals(1.5, r.rank(), 1e-9);
        assertEquals(10, r.firstLine());
        assertEquals(25, r.lastLine());
    }

    @Test
    void methodResultEqualityIsStructural() {
        var a = new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 1.5, 10, 25);
        var b = new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 1.5, 10, 25);
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void methodResultInequalityOnEachField() {
        var a = new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 1.5, 10, 25);
        assertNotEquals(a, new MethodResult("com.example.Bar", "doWork", "(I)V", 0.9, 1.5, 10, 25));
        assertNotEquals(a, new MethodResult("com.example.Foo", "other", "(I)V", 0.9, 1.5, 10, 25));
        assertNotEquals(a, new MethodResult("com.example.Foo", "doWork", "()V", 0.9, 1.5, 10, 25));
        assertNotEquals(a, new MethodResult("com.example.Foo", "doWork", "(I)V", 0.5, 1.5, 10, 25));
        assertNotEquals(a, new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 2.0, 10, 25));
        assertNotEquals(a, new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 1.5, 11, 25));
        assertNotEquals(a, new MethodResult("com.example.Foo", "doWork", "(I)V", 0.9, 1.5, 10, 26));
    }

    @Test
    void methodResultImplementsLocalizationResult() {
        LocalizationResult r = new MethodResult("com.example.X", "run", "()V", 0.8, 2.5, 5, 15);
        assertEquals("com.example.X", r.fullyQualifiedClass());
        assertEquals(0.8, r.score(), 1e-9);
        assertEquals(2.5, r.rank(), 1e-9);
    }

    @Test
    void methodResultToStringContainsKeyFields() {
        var r = new MethodResult("com.example.Foo", "doWork", "(I)V", 0.707, 1.5, 10, 25);
        String str = r.toString();
        assertTrue(str.contains("com.example.Foo"));
        assertTrue(str.contains("doWork"));
        assertTrue(str.contains("10"));
        assertTrue(str.contains("25"));
    }

    // ── RankGroup ─────────────────────────────────────────────────────────

    @Test
    void rankGroupFieldsAreAccessible() {
        var r1 = new StatementResult("com.example.A", 10, 0.7, 1.0);
        var r2 = new StatementResult("com.example.B", 20, 0.7, 1.0);
        var group = new RankGroup(1.0, 0.7, List.of(r1, r2), 2);

        assertEquals(1.0, group.rank(), 1e-9);
        assertEquals(0.7, group.score(), 1e-9);
        assertEquals(2, group.results().size());
        assertEquals(2, group.topN());
    }

    @Test
    void rankGroupWithFractionalRank() {
        var r1 = new MethodResult("com.example.A", "m1", "()V", 0.7, 1.5, 1, 10);
        var r2 = new MethodResult("com.example.B", "m2", "()V", 0.7, 1.5, 5, 20);
        var group = new RankGroup(1.5, 0.7, List.of(r1, r2), 2);

        assertEquals(1.5, group.rank(), 1e-9);
        assertEquals(2, group.results().size());
    }

    @Test
    void rankGroupEqualityIsStructural() {
        var r = new StatementResult("com.example.A", 10, 0.5, 1.0);
        var g1 = new RankGroup(1.0, 0.5, List.of(r), 1);
        var g2 = new RankGroup(1.0, 0.5, List.of(r), 1);
        assertEquals(g1, g2);
    }

    @Test
    void rankGroupAcceptsMethodResults() {
        var mr = new MethodResult("com.example.A", "run", "()V", 0.9, 1.0, 5, 15);
        var group = new RankGroup(1.0, 0.9, List.of(mr), 1);
        assertInstanceOf(MethodResult.class, group.results().get(0));
    }

    // ── CoreProcessResult ─────────────────────────────────────────────────

    @Test
    void coreProcessResultFieldsAreAccessible() {
        var result = new CoreProcessResult(0, "stdout content", "stderr content");
        assertEquals(0, result.exitCode());
        assertEquals("stdout content", result.stdout());
        assertEquals("stderr content", result.stderr());
    }

    @Test
    void coreProcessResultEquality() {
        var a = new CoreProcessResult(2, "out", "err");
        var b = new CoreProcessResult(2, "out", "err");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
    }
}
