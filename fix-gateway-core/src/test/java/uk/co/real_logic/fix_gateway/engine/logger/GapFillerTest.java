/*
 * Copyright 2015-2016 Real Logic Ltd.
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
package uk.co.real_logic.fix_gateway.engine.logger;

import org.junit.Test;
import uk.co.real_logic.aeron.Subscription;
import uk.co.real_logic.fix_gateway.decoder.ResendRequestDecoder;
import uk.co.real_logic.fix_gateway.decoder.SequenceResetDecoder;
import uk.co.real_logic.fix_gateway.messages.MessageStatus;
import uk.co.real_logic.fix_gateway.streams.GatewayPublication;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GapFillerTest extends AbstractLogTest
{

    private GatewayPublication publication = mock(GatewayPublication.class);
    private Subscription subscription = mock(Subscription.class);
    private GapFiller gapFiller = new GapFiller(subscription, publication);

    @Test
    public void shouldGapFillInResponseToResendRequest()
    {
        bufferHasResendRequest(END_SEQ_NO);
        gapFiller.onMessage(
            buffer, 1, buffer.capacity(), LIBRARY_ID, CONNECTION_ID, SESSION_ID, ResendRequestDecoder.MESSAGE_TYPE, 0L);

        verify(publication).saveMessage(
            any(), eq(0), anyInt(),
            eq(LIBRARY_ID), eq(SequenceResetDecoder.MESSAGE_TYPE), eq(SESSION_ID), eq(CONNECTION_ID),
            eq(MessageStatus.OK));
    }
}