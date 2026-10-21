package org.torproject.vpn.suite;

import androidx.test.filters.LargeTest;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;
import org.torproject.vpn.ui.approuting.AppRoutingFragmentTest;
import org.torproject.vpn.ui.help.HelpFragmentTest;
import org.torproject.vpn.ui.home.ConnectFragmentTest;

@LargeTest
@RunWith(Suite.class)
@Suite.SuiteClasses({
        ConnectFragmentTest.class,
        AppRoutingFragmentTest.class,
        HelpFragmentTest.class
})
public class TestSuite {
}
