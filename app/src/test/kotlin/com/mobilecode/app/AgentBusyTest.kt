package com.mobilecode.app

import org.junit.Assert.*
import org.junit.Test

class AgentBusyTest {
    @Test fun noSessionsMeansIdle() = assertFalse(isAgentBusy("{}"))
    @Test fun aBusySessionMeansBusy() = assertTrue(isAgentBusy("""{"ses_1":{"type":"busy"}}"""))
    @Test fun retryingCountsAsBusy() = assertTrue(isAgentBusy("""{"ses_1":{"type":"idle"},"ses_2":{"type":"retry","attempt":2}}"""))
    @Test fun idleSessionsAreIdle() = assertFalse(isAgentBusy("""{"ses_1":{"type":"idle"},"ses_2":{"type":"idle"}}"""))
    @Test fun garbageIsIdle() { assertFalse(isAgentBusy("")); assertFalse(isAgentBusy("not json")); assertFalse(isAgentBusy("[1,2]")); assertFalse(isAgentBusy("""{"a":5}""")) }
}
