/*
 * Copyright 2015-2017 Real Logic Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package uk.co.real_logic.artio.session;

import org.junit.Test;
import org.mockito.verification.VerificationMode;
import uk.co.real_logic.artio.util.MutableAsciiBuffer;

import static io.aeron.logbuffer.ControlledFragmentHandler.Action.CONTINUE;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.*;
import static uk.co.real_logic.artio.CommonConfiguration.DEFAULT_SESSION_BUFFER_SIZE;
import static uk.co.real_logic.artio.engine.EngineConfiguration.DEFAULT_REASONABLE_TRANSMISSION_TIME_IN_MS;
import static uk.co.real_logic.artio.messages.SessionState.*;
import static uk.co.real_logic.artio.session.SessionProxy.NO_LAST_MSG_SEQ_NUM_PROCESSED;

public class InitiatorSessionTest extends AbstractSessionTest
{
    private InitiatorSession session;

    {
        session = new InitiatorSession(HEARTBEAT_INTERVAL,
            CONNECTION_ID,
            fakeClock,
            mockProxy,
            mockPublication,
            idStrategy,
            SENDING_TIME_WINDOW,
            mockReceivedMsgSeqNo,
            mockSentMsgSeqNo,
            LIBRARY_ID,
            1,
            SEQUENCE_INDEX,
            CONNECTED,
            false,
            DEFAULT_REASONABLE_TRANSMISSION_TIME_IN_MS,
            new MutableAsciiBuffer(new byte[DEFAULT_SESSION_BUFFER_SIZE]),
            false);
        session.logonListener(mockLogonListener);
    }

    @Test
    public void shouldInitiallyBeConnected()
    {
        assertEquals(CONNECTED, session.state());
    }

    @Test
    public void shouldActivateUponLogonResponse()
    {
        session.state(SENT_LOGON);

        assertEquals(CONTINUE, onLogon(1));

        assertState(ACTIVE);
        verify(mockProxy).setupSession(SESSION_ID, SESSION_KEY);
        verifyNoFurtherMessages();
    }

    @Test
    public void shouldAttemptLogonWhenConnected()
    {
        session.id(SESSION_ID);
        session.poll(0);

        verifyLogon();

        assertEquals(1, session.lastSentMsgSeqNum());
    }

    @Test
    public void shouldAttemptLogonOnlyOnce()
    {
        session.id(SESSION_ID);
        session.poll(0);

        session.poll(10);

        session.poll(20);

        verifyLogon();
    }

    @Test
    public void shouldNotifyGatewayWhenLoggedIn()
    {
        session.state(SENT_LOGON);

        assertEquals(CONTINUE, onLogon(1));

        verifySavesLogonMessage(times(1));
    }

    @Test
    public void shouldNotifyGatewayWhenLoggedInOnce()
    {
        session.state(SENT_LOGON);

        assertEquals(CONTINUE, onLogon(1));

        assertEquals(CONTINUE, onLogon(2));

        verifySavesLogonMessage(times(1));
    }

    @Test
    public void shouldStartAcceptLogonBasedSequenceNumberResetWhenSequenceNumberIsOne()
    {
        shouldStartAcceptLogonBasedSequenceNumberResetWhenSequenceNumberIsOne(SEQUENCE_INDEX);
    }

    private void verifySavesLogonMessage(final VerificationMode verificationMode)
    {
        verify(mockLogonListener, verificationMode).onLogon(any());
    }

    private void verifyLogon()
    {
        verify(mockProxy, times(1)).logon(
            HEARTBEAT_INTERVAL, 1, null, null, false, SEQUENCE_INDEX, NO_LAST_MSG_SEQ_NUM_PROCESSED);
    }

    protected void readyForLogon()
    {
        session.state(SENT_LOGON);
    }

    protected Session session()
    {
        return session;
    }
}
