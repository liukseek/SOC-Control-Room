package it.soc.server;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import it.soc.server.game.GameStats;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NetworkSafetyTest {

    @Test
    void lineReaderSplitsLinesAndHandlesCarriageReturns() throws IOException {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("uno\r\ndue\ntre"), 100);

        assertEquals("uno", reader.readLine());
        assertEquals("due", reader.readLine());
        assertEquals("tre", reader.readLine());
        assertNull(reader.readLine());
    }

    @Test
    void lineReaderRejectsOversizedLines() {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("x".repeat(50) + "\n"), 10);

        assertThrows(IOException.class, reader::readLine);
    }

    @Test
    void lineReaderAcceptsLinesAtTheLimit() throws IOException {
        BoundedLineReader reader = new BoundedLineReader(new StringReader("x".repeat(10) + "\n"), 10);

        assertEquals(10, reader.readLine().length());
    }

    @Test
    void rateLimiterAllowsBurstsUpToTheLimitThenCountsStrikes() {
        MessageRateLimiter limiter = new MessageRateLimiter(3, 1000);

        assertTrue(limiter.allow(0));
        assertTrue(limiter.allow(10));
        assertTrue(limiter.allow(20));
        assertFalse(limiter.allow(30));
        assertFalse(limiter.allow(40));
        assertEquals(2, limiter.strikes());
    }

    @Test
    void rateLimiterResetsWhenTheWindowPasses() {
        MessageRateLimiter limiter = new MessageRateLimiter(1, 1000);

        assertTrue(limiter.allow(0));
        assertFalse(limiter.allow(500));
        assertTrue(limiter.allow(1500));
    }

    @Test
    void statsAccumulateAndPersist(@TempDir Path folder) throws IOException {
        Path file = folder.resolve("statistiche.properties");

        GameStats stats = new GameStats(file);
        stats.record("Alice", true, 150);
        stats.record("alice", false, 40);
        stats.save();

        assertTrue(Files.isRegularFile(file));

        GameStats reloaded = new GameStats(file);

        assertEquals(2, reloaded.games("Alice"));
        assertEquals(1, reloaded.wins("Alice"));
        assertEquals(190, reloaded.points("Alice"));
        assertEquals("Partite 2 · Vittorie 1 · Punti 190", reloaded.summary("ALICE"));
        assertEquals(0, reloaded.games("Bruno"));
    }

    @Test
    void difficultyParsesNamesAndLabels() {
        assertEquals(GameConfig.Difficulty.HARD, GameConfig.Difficulty.parse("hard"));
        assertEquals(GameConfig.Difficulty.EASY, GameConfig.Difficulty.parse("Facile"));
        assertNull(GameConfig.Difficulty.parse("impossibile"));
    }
}
