package com.vibeflow.mobile.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MathSuggestTest {

    // Results only appear AFTER the user types "=".
    @Test fun additionWithEquals() = assertEquals("3", MathSuggest.compute("1 + 2 ="))
    @Test fun noSpaces() = assertEquals("4", MathSuggest.compute("2+2="))
    @Test fun precedence() = assertEquals("14", MathSuggest.compute("2 + 3 * 4 ="))
    @Test fun parentheses() = assertEquals("20", MathSuggest.compute("(2 + 3) * 4 ="))
    @Test fun division() = assertEquals("2.5", MathSuggest.compute("5 / 2 ="))
    @Test fun unicodeOperators() = assertEquals("6", MathSuggest.compute("2 × 3 ="))
    @Test fun trailingInProse() = assertEquals("7", MathSuggest.compute("the total is 3 + 4 ="))

    // No "=" yet → no result (this is the behaviour change).
    @Test fun noResultBeforeEquals() = assertNull(MathSuggest.compute("1 + 2"))
    @Test fun noResultMidExpression() = assertNull(MathSuggest.compute("1 + 2 * "))

    // A second expression later on the same line still computes (latest one wins).
    @Test fun secondExpressionSameLine() = assertEquals("13", MathSuggest.compute("1+2=3   6+7="))
    @Test fun secondExpressionWithSpaces() = assertEquals("13", MathSuggest.compute("1+2=3   6 + 7 ="))

    @Test fun plainNumberIsNotMath() = assertNull(MathSuggest.compute("12345="))
    @Test fun wordIsNotMath() = assertNull(MathSuggest.compute("hello="))
    @Test fun divideByZeroIsNull() = assertNull(MathSuggest.compute("5 / 0 ="))
}
