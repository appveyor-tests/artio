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
package uk.co.real_logic.fix_gateway.engine.framer;

import io.aeron.Image;
import uk.co.real_logic.fix_gateway.protocol.GatewayPublication;

import java.util.function.Consumer;

class PositionSender implements Consumer<Image>
{
    private final GatewayPublication publication;

    PositionSender(final GatewayPublication publication)
    {
        this.publication = publication;
    }

    public void accept(final Image image)
    {
        // Willingly drop the position message in case of back pressure
        publication.saveNewSentPosition(image.sessionId(), image.position());
    }
}
