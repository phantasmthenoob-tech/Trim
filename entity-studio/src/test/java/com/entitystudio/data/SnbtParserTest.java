package com.entitystudio.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SnbtParserTest {

    @Test
    void parsesTypedScalars() {
        SnbtParser.Result r = SnbtParser.parse(
                "{Health:20.0f,Age:3,Fire:-1s,OnGround:1b,Id:99l,Name:\"Bob\",Flag:true}");
        assertTrue(r.ok(), r.error());
        assertEquals(new SnbtParser.Scalar("float", "20.0f"), r.root().get("Health"));
        assertEquals(new SnbtParser.Scalar("int", "3"), r.root().get("Age"));
        assertEquals(new SnbtParser.Scalar("short", "-1s"), r.root().get("Fire"));
        assertEquals(new SnbtParser.Scalar("byte", "1b"), r.root().get("OnGround"));
        assertEquals(new SnbtParser.Scalar("long", "99l"), r.root().get("Id"));
        assertEquals(new SnbtParser.Scalar("string", "Bob"), r.root().get("Name"));
        assertEquals(new SnbtParser.Scalar("boolean", "true"), r.root().get("Flag"));
    }

    @Test
    void parsesNestingListsAndArrays() {
        SnbtParser.Result r = SnbtParser.parse(
                "{Pos:[1.5d,64.0d,-3.25d],Inv:[{Slot:0b,id:\"stone\"}],A:[B;1b,2b],B:[I;7],C:[L;9l]}");
        assertTrue(r.ok(), r.error());
        assertInstanceOf(List.class, r.root().get("Pos"));
        assertEquals(3, ((List<?>) r.root().get("Pos")).size());
        assertEquals(new SnbtParser.Scalar("double", "1.5d"),
                ((List<?>) r.root().get("Pos")).get(0));
        Map<?, ?> slot = (Map<?, ?>) ((List<?>) r.root().get("Inv")).get(0);
        assertEquals(new SnbtParser.Scalar("string", "stone"), slot.get("id"));
        assertEquals(new SnbtParser.Scalar("byte[]", "[1b, 2b]"), r.root().get("A"));
        assertEquals(new SnbtParser.Scalar("int[]", "[7]"), r.root().get("B"));
        assertEquals(new SnbtParser.Scalar("long[]", "[9l]"), r.root().get("C"));
    }

    @Test
    void parsesQuotesAndEscapes() {
        SnbtParser.Result r = SnbtParser.parse("{a:'it\\'s',b:\"q\\\"q\",c:\"uni\\u00e9\"}");
        assertTrue(r.ok(), r.error());
        assertEquals("it's", ((SnbtParser.Scalar) r.root().get("a")).text());
        assertEquals("q\"q", ((SnbtParser.Scalar) r.root().get("b")).text());
        assertEquals("unié", ((SnbtParser.Scalar) r.root().get("c")).text());
    }

    @Test
    void rejectsMalformedInput() {
        assertFalse(SnbtParser.parse("{a:}").ok());
        assertFalse(SnbtParser.parse("{a 1}").ok());
        assertFalse(SnbtParser.parse("{a:'x}").ok());
        assertFalse(SnbtParser.parse("{a:1},trailing").ok());
        assertFalse(SnbtParser.parse("[1,2]").ok());
        assertFalse(SnbtParser.parse("").ok());
        assertFalse(SnbtParser.parse(null).ok());
        // Unquoted non-numeric values are not valid SNBT: refuse, never guess.
        assertFalse(SnbtParser.parse("{a:foo}").ok());
    }

    @Test
    void rejectsRunawayInput() {
        // Too deep.
        String deep = "{n:".repeat(12) + "1" + "}".repeat(12);
        SnbtParser.Result d = SnbtParser.parse(deep);
        assertFalse(d.ok());
        // Too many nodes.
        String big = "{a:[" + "1,".repeat(5000) + "1]}";
        SnbtParser.Result b = SnbtParser.parse(big);
        assertFalse(b.ok());
        assertNotNull(b.error());
        // Too many characters outright.
        SnbtParser.Result huge = SnbtParser.parse("{a:\"" + "x".repeat(70000) + "\"}");
        assertFalse(huge.ok());
        assertTrue(huge.error().contains("too large"));
    }
}
