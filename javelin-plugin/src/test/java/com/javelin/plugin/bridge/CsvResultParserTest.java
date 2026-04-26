package com.javelin.plugin.bridge;

import com.javelin.plugin.model.LocalizationResult;
import com.javelin.plugin.model.MethodResult;
import com.javelin.plugin.model.StatementResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CsvResultParserTest {

    private CsvResultParser parser;

    @BeforeEach
    void setUp() {
        parser = new CsvResultParser();
    }

    // ── Statement-level parsing ────────────────────────────────────────────

    @Test
    void parsesTypicalStatementCsv(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("results.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.Foo,42,0.707107,1
                com.example.Bar,18,0.500000,2
                com.example.Baz,99,0.353553,3
                """);

        List<LocalizationResult> results = parser.parse(csv);

        assertEquals(3, results.size());

        StatementResult first = (StatementResult) results.get(0);
        assertEquals("com.example.Foo", first.fullyQualifiedClass());
        assertEquals(42, first.lineNumber());
        assertEquals(0.707107, first.score(), 1e-6);
        assertEquals(1.0, first.rank(), 1e-9);

        StatementResult second = (StatementResult) results.get(1);
        assertEquals("com.example.Bar", second.fullyQualifiedClass());
        assertEquals(18, second.lineNumber());
        assertEquals(0.500000, second.score(), 1e-6);
        assertEquals(2.0, second.rank(), 1e-9);

        StatementResult third = (StatementResult) results.get(2);
        assertEquals("com.example.Baz", third.fullyQualifiedClass());
        assertEquals(99, third.lineNumber());
        assertEquals(0.353553, third.score(), 1e-6);
        assertEquals(3.0, third.rank(), 1e-9);
    }

    @Test
    void returnsEmptyForEmptyFile(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("empty.csv");
        Files.writeString(csv, "");

        List<LocalizationResult> results = parser.parse(csv);
        assertTrue(results.isEmpty());
    }

    @Test
    void returnsEmptyForHeaderOnly(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("header_only.csv");
        Files.writeString(csv, "FullyQualifiedClass,LineNumber,OchiaiScore,Rank\n");

        List<LocalizationResult> results = parser.parse(csv);
        assertTrue(results.isEmpty());
    }

    @Test
    void skipsStatementRowsWithFewerThanFourColumns(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("malformed.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.Good,10,0.5,1
                only,two
                com.example.AlsoGood,20,0.3,2
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(2, results.size());
        assertEquals("com.example.Good", results.get(0).fullyQualifiedClass());
        assertEquals("com.example.AlsoGood", results.get(1).fullyQualifiedClass());
    }

    @Test
    void unquotesClassNameWithDoubleQuotes(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("quoted.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                "com.example.Quoted",15,0.8,1
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertEquals("com.example.Quoted", results.get(0).fullyQualifiedClass());
    }

    @Test
    void handlesEscapedQuotesInClassName(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("escaped.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                "com.example.Has""Quote",15,0.8,1
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertEquals("com.example.Has\"Quote", results.get(0).fullyQualifiedClass());
    }

    @Test
    void handlesWhitespaceAroundValues(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("whitespace.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                 com.example.Foo , 42 , 0.707107 , 1
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertEquals("com.example.Foo", results.get(0).fullyQualifiedClass());
        assertEquals(42, ((StatementResult) results.get(0)).lineNumber());
    }

    @Test
    void parsesZeroScoreAndRank(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("zeros.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.Safe,1,0.0,10
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertEquals(0.0, results.get(0).score(), 1e-9);
        assertEquals(10.0, results.get(0).rank(), 1e-9);
    }

    @Test
    void parsesLargeStatementResultSet(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("large.csv");
        StringBuilder sb = new StringBuilder("FullyQualifiedClass,LineNumber,OchiaiScore,Rank\n");
        for (int i = 1; i <= 500; i++) {
            sb.append("com.example.Class").append(i).append(",").append(i)
              .append(",").append(1.0 / i).append(",").append(i).append("\n");
        }
        Files.writeString(csv, sb.toString());

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(500, results.size());
        assertEquals("com.example.Class1", results.get(0).fullyQualifiedClass());
        assertEquals("com.example.Class500", results.get(499).fullyQualifiedClass());
    }

    @Test
    void throwsOnInvalidLineNumber(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("bad_line.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.Foo,notAnInt,0.5,1
                """);

        assertThrows(NumberFormatException.class, () -> parser.parse(csv));
    }

    @Test
    void throwsOnInvalidScore(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("bad_score.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.Foo,10,notADouble,1
                """);

        assertThrows(NumberFormatException.class, () -> parser.parse(csv));
    }

    @Test
    void throwsOnNonexistentFile() {
        Path nonexistent = Path.of("/this/does/not/exist/results.csv");
        assertThrows(IOException.class, () -> parser.parse(nonexistent));
    }

    @Test
    void handlesExtraColumnsGracefully(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("extra_cols.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank,Extra
                com.example.Foo,42,0.707107,1,ignored
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertEquals("com.example.Foo", results.get(0).fullyQualifiedClass());
    }

    @Test
    void preservesInsertionOrder(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("order.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.C,30,0.3,3
                com.example.A,10,0.7,1
                com.example.B,20,0.5,2
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals("com.example.C", results.get(0).fullyQualifiedClass());
        assertEquals("com.example.A", results.get(1).fullyQualifiedClass());
        assertEquals("com.example.B", results.get(2).fullyQualifiedClass());
    }

    @Test
    void parsesStatementRankAsDouble(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("rank_double.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,LineNumber,OchiaiScore,Rank
                com.example.Foo,10,0.7,2
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertEquals(2.0, results.get(0).rank(), 1e-9);
        assertInstanceOf(StatementResult.class, results.get(0));
    }

    // ── Method-level parsing ──────────────────────────────────────────────

    @Test
    void detectsMethodLevelFormatFromHeader(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("method.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,MethodName,Descriptor,OchiaiScore,Rank,FirstLine,LastLine
                com.example.Foo,doWork,(I)V,0.707107,1.0,10,25
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertInstanceOf(MethodResult.class, results.get(0));

        MethodResult mr = (MethodResult) results.get(0);
        assertEquals("com.example.Foo", mr.fullyQualifiedClass());
        assertEquals("doWork", mr.methodName());
        assertEquals("(I)V", mr.descriptor());
        assertEquals(0.707107, mr.score(), 1e-6);
        assertEquals(1.0, mr.rank(), 1e-9);
        assertEquals(10, mr.firstLine());
        assertEquals(25, mr.lastLine());
    }

    @Test
    void parsesMultipleMethodResults(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("methods.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,MethodName,Descriptor,OchiaiScore,Rank,FirstLine,LastLine
                com.example.Foo,methodA,()V,0.9,1.0,5,15
                com.example.Foo,methodB,(I)Z,0.5,2.0,20,30
                com.example.Bar,methodC,()Ljava/lang/String;,0.3,3.0,1,10
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(3, results.size());
        assertInstanceOf(MethodResult.class, results.get(0));
        assertInstanceOf(MethodResult.class, results.get(1));
        assertInstanceOf(MethodResult.class, results.get(2));

        MethodResult second = (MethodResult) results.get(1);
        assertEquals("com.example.Foo", second.fullyQualifiedClass());
        assertEquals("methodB", second.methodName());
        assertEquals(2.0, second.rank(), 1e-9);
    }

    @Test
    void parsesAverageFractionalRankInMethodLevel(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("fractional_rank.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,MethodName,Descriptor,OchiaiScore,Rank,FirstLine,LastLine
                com.example.Foo,m1,()V,0.7,1.5,1,10
                com.example.Bar,m2,()V,0.7,1.5,5,20
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(2, results.size());
        assertEquals(1.5, results.get(0).rank(), 1e-9);
        assertEquals(1.5, results.get(1).rank(), 1e-9);
    }

    @Test
    void skipsMethodRowsWithFewerThanSevenColumns(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("short_method.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,MethodName,Descriptor,OchiaiScore,Rank,FirstLine,LastLine
                com.example.Foo,doWork,(I)V,0.7,1.0,10,25
                com.example.Bad,broken
                com.example.Good,valid,()V,0.5,2.0,1,5
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(2, results.size());
    }

    @Test
    void methodHeaderDetectionIsCaseInsensitiveToValue(@TempDir Path tempDir) throws IOException {
        Path csv = tempDir.resolve("method_header.csv");
        Files.writeString(csv, """
                FullyQualifiedClass,MethodName,Descriptor,OchiaiScore,Rank,FirstLine,LastLine
                com.example.A,run,()V,0.5,1.0,1,5
                """);

        List<LocalizationResult> results = parser.parse(csv);
        assertEquals(1, results.size());
        assertInstanceOf(MethodResult.class, results.get(0));
    }
}
