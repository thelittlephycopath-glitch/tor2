package org.torproject.vpn.utils

import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.ViewAssertion
import androidx.test.espresso.ViewInteraction
import junit.framework.AssertionFailedError

object CustomInteractions {
    fun tryResolve(viewInteraction: ViewInteraction, maxTries: Int): ViewInteraction {
        return tryResolve(viewInteraction, null, maxTries)
    }

    @JvmOverloads
    fun tryResolve(
        viewInteraction: ViewInteraction,
        assertion: ViewAssertion?,
        maxTries: Int = 10
    ): ViewInteraction {
        repeat(maxTries) { i ->
            try {
                if (assertion != null) {
                    viewInteraction.check(assertion)
                }
                return viewInteraction
            } catch (exception: Throwable) {
                when (exception) {
                    is AssertionFailedError,
                    is NoMatchingViewException -> {
                        println("NoMatchingViewException attempt: $i")
                        exception.printStackTrace()
                        if (i == maxTries) {
                            throw exception
                        }
                        try {
                            Thread.sleep(1000)
                        } catch (e: InterruptedException) {
                            e.printStackTrace()
                        }
                    }
                    else -> throw exception
                }
            }
        }

        throw java.lang.AssertionError("failed to resolve viewInteraction")
    }
}
