package org.torproject.vpn.utils

import androidx.appcompat.widget.Toolbar
import androidx.test.espresso.matcher.BoundedMatcher
import com.google.android.material.appbar.CollapsingToolbarLayout
import org.hamcrest.Description
import org.hamcrest.Matcher

object CustomMatchers {

    fun withCollapsibleToolbarTitle(textMatcher: Matcher<String?>): Matcher<Any?> {
        return object :
            BoundedMatcher<Any?, CollapsingToolbarLayout>(CollapsingToolbarLayout::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("with toolbar title: ")
                textMatcher.describeTo(description)
            }

            override fun matchesSafely(toolbarLayout: CollapsingToolbarLayout): Boolean {
                return textMatcher.matches(toolbarLayout.title)
            }
        }
    }

    fun withToolbarTitle(textMatcher: Matcher<String?>): Matcher<Any?> {
        return object :
            BoundedMatcher<Any?, Toolbar>(Toolbar::class.java) {
            override fun describeTo(description: Description) {
                description.appendText("with toolbar title: ")
                textMatcher.describeTo(description)
            }

            override fun matchesSafely(toolbar: Toolbar): Boolean {
                return textMatcher.matches(toolbar.title)
            }

            override fun describeMismatch(item: Any?, description: Description?) {
                super.describeMismatch(item, description)
            }
        }
    }
}
