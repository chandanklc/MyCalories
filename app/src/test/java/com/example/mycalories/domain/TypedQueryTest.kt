package com.example.mycalories.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TypedQueryTest {

    @Test
    fun `a leading number is the quantity and the rest is the search`() {
        assertEquals(TypedQuery(2.0, "pan"), TypedQuery.parse("2 pan"))
        assertEquals(TypedQuery(1.5, "roti"), TypedQuery.parse("1.5 roti"))
        assertEquals(TypedQuery(3.0, "idli"), TypedQuery.parse("3x idli"))
        assertEquals(TypedQuery(3.0, "idli"), TypedQuery.parse("3 × idli"))
    }

    @Test
    fun `no number means one`() {
        assertEquals(TypedQuery(1.0, "paneer paratha"), TypedQuery.parse("  Paneer   paratha "))
    }

    @Test
    fun `half and fractions work`() {
        assertEquals(TypedQuery(0.5, "roti"), TypedQuery.parse("half roti"))
        assertEquals(TypedQuery(0.5, "roti"), TypedQuery.parse("½ roti"))
        assertEquals(TypedQuery(2.5, "roti"), TypedQuery.parse("2½ roti"))
    }

    @Test
    fun `small number words are understood`() {
        assertEquals(TypedQuery(2.0, "roti"), TypedQuery.parse("two roti"))
        assertEquals(TypedQuery(1.0, "banana"), TypedQuery.parse("one banana"))
        assertEquals(TypedQuery(1.0, "banana"), TypedQuery.parse("a banana"))
    }

    @Test
    fun `just a number or nothing gives an empty search`() {
        assertEquals(TypedQuery(4.0, ""), TypedQuery.parse("4"))
        assertEquals(TypedQuery(1.0, ""), TypedQuery.parse(""))
    }

    @Test
    fun `zero or silly amounts fall back to one`() {
        assertEquals(TypedQuery(1.0, "roti"), TypedQuery.parse("0 roti"))
    }
}
